import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
SHA = "a" * 40
DIGEST = "sha256:" + "b" * 64


class RunnerPublishTest(unittest.TestCase):
    def run_publish(self, mode):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "performance").mkdir()
            (root / "bin").mkdir()
            for file in ("performance/publish-runner-image.sh", "performance/build-runner-image.sh", "bin/publish-runner-image"):
                shutil.copy2(ROOT / file, root / file)
            fake_bin = root / "fake-bin"
            fake_bin.mkdir()
            commands = {
                "git": f"#!/bin/sh\necho {SHA}\n",
                "docker": '#!/bin/sh\necho "docker $*" >> "$CALLS"\nif [ "$1" = login ]; then cat >/dev/null; fi\n',
                "aws": '''#!/usr/bin/env python3
import json, os, sys
with open(os.environ['CALLS'], 'a') as f: f.write('aws ' + ' '.join(sys.argv[1:]) + '\\n')
command = sys.argv[2]
mode = os.environ['MODE']
if command == 'describe-repositories':
    print(json.dumps({'repositories': [{'repositoryUri': 'registry.example/runner',
        'imageTagMutability': 'MUTABLE' if mode == 'mutable' else 'IMMUTABLE'}]}))
elif command == 'batch-get-image':
    if mode == 'denied': sys.exit(1)
    if mode == 'exists': print(json.dumps({'images': [{}], 'failures': []}))
    else: print(json.dumps({'images': [], 'failures': [{'failureCode': 'ImageNotFound'}]}))
elif command == 'describe-images':
    print('sha256:' + 'b' * 64)
elif command == 'get-login-password': print('fake-token')
else: sys.exit(2)
''',
            }
            for name, body in commands.items():
                file = fake_bin / name
                file.write_text(body)
                file.chmod(0o755)
            calls = root / "calls"
            env = dict(os.environ, PATH=f"{fake_bin}:{os.environ['PATH']}", CALLS=str(calls), MODE=mode,
                       AWS_REGION="ap-northeast-2", RUNNER_ECR_REPOSITORY="runner",
                       GITHUB_OUTPUT=str(root / "output"), GITHUB_STEP_SUMMARY=str(root / "summary"))
            result = subprocess.run([str(root / "performance/publish-runner-image.sh")], env=env,
                                    capture_output=True, text=True)
            artifact = root / "runner-image.json"
            return result, calls.read_text(), json.loads(artifact.read_text()) if artifact.exists() else None

    def test_new_tag_builds_verifies_and_pushes_with_source_identity(self):
        result, calls, artifact = self.run_publish("new")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertLess(calls.index("docker build"), calls.index("docker run"))
        self.assertLess(calls.index("docker run"), calls.index("docker push"))
        self.assertIn("--network none", calls)
        self.assertIn(f"APP_REVISION={SHA}", calls)
        self.assertEqual(SHA, artifact["source_commit_sha"])
        self.assertEqual(SHA, artifact["image_tag"])
        self.assertEqual(DIGEST, artifact["image_digest"])
        self.assertEqual(f"registry.example/runner@{DIGEST}", artifact["image_digest_uri"])

    def test_existing_immutable_tag_is_reused(self):
        result, calls, artifact = self.run_publish("exists")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn("docker", calls)
        self.assertEqual(DIGEST, artifact["image_digest"])

    def test_mutable_repository_and_lookup_errors_do_not_publish(self):
        for mode in ("mutable", "denied"):
            with self.subTest(mode=mode):
                result, calls, artifact = self.run_publish(mode)
                self.assertNotEqual(0, result.returncode)
                self.assertNotIn("docker", calls)
                self.assertIsNone(artifact)

    def test_workflow_is_manual_and_tests_before_publish_without_ecs(self):
        workflow = yaml.load((ROOT / ".github/workflows/performance-runner-publish.yml").read_text(), Loader=yaml.BaseLoader)
        self.assertEqual(["workflow_dispatch"], list(workflow["on"]))
        self.assertEqual(["publish"], list(workflow["jobs"]))
        steps = workflow["jobs"]["publish"]["steps"]
        scripts = [step.get("run", "") for step in steps]
        self.assertLess(next(i for i, s in enumerate(scripts) if "unittest discover" in s),
                        next(i for i, s in enumerate(scripts) if "publish-runner-image.sh" in s))
        text = "\n".join(scripts) + (ROOT / "performance/publish-runner-image.sh").read_text()
        for forbidden in ("aws ecs", "bootBuildImage", "register-task-definition", "update-service"):
            self.assertNotIn(forbidden, text)
