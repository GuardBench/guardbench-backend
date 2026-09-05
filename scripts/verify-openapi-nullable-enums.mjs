import { readFileSync } from 'node:fs';

const openapiPath = process.argv[2] ?? 'docs/api/openapi.yaml';
const lines = readFileSync(openapiPath, 'utf8').split(/\r?\n/);
const schemas = new Map();
let currentSchema;
let inSchemas = false;

for (const line of lines) {
  if (line === '  schemas:') {
    inSchemas = true;
    continue;
  }
  if (!inSchemas) continue;
  if (/^  \S/.test(line)) break;

  const schemaMatch = line.match(/^    ([A-Za-z0-9]+):\s*$/);
  if (schemaMatch) {
    currentSchema = { nullable: false, enumValues: undefined, lines: [] };
    schemas.set(schemaMatch[1], currentSchema);
    continue;
  }
  if (!currentSchema) continue;
  currentSchema.lines.push(line);
  if (/^      nullable: true\s*$/.test(line)) currentSchema.nullable = true;

  const enumMatch = line.match(/^      enum: \[(.*)]\s*$/);
  if (enumMatch) {
    currentSchema.enumValues = enumMatch[1].split(',').map((value) => value.trim());
  }
}

const failures = [];
const nullableSchemas = [...schemas.entries()].filter(([name]) => name.startsWith('Nullable'));
const nullableFieldRefs = [
  ['TestRunListItemRes', 'executionOutcome', 'NullableTestRunExecutionOutcome'],
  ['TestRunListItemRes', 'qualityGateStatus', 'NullableQualityGateStatus'],
  ['TestRunDetailRes', 'executionOutcome', 'NullableTestRunExecutionOutcome'],
  ['TestRunDetailRes', 'qualityGate', 'QualityGateRes'],
  ['TestRunResultItemRes', 'evaluatorVerdict', 'NullableAction'],
  ['TestRunResultItemRes', 'assertionStatus', 'NullableAssertionStatus'],
  ['TestRunResultItemRes', 'evaluationOutcome', 'NullableEvaluationOutcome'],
  ['TestRunComparisonItemRes', 'comparisonVerdict', 'NullableAction'],
  ['TestRunComparisonItemRes', 'currentVerdict', 'NullableAction'],
  ['TestRunComparisonItemRes', 'changeType', 'NullableRegressionChangeType'],
];
const comparisonSummaryFields = [
  'currentRunId',
  'comparisonRunId',
  'totalCases',
  'changedCount',
  'unchangedCount',
  'improvedCount',
  'regressedCount',
  'notComparableCount',
];

if (nullableSchemas.length === 0) {
  failures.push('Nullable* schema를 찾지 못했습니다.');
}

for (const [nullableName, nullableSchema] of nullableSchemas) {
  const baseName = nullableName.slice('Nullable'.length);
  const baseSchema = schemas.get(baseName);
  if (!baseSchema) {
    failures.push(`${nullableName}: 기반 schema ${baseName}가 없습니다.`);
    continue;
  }
  if (!nullableSchema.nullable) {
    failures.push(`${nullableName}: nullable: true가 필요합니다.`);
  }
  if (!baseSchema.enumValues || !nullableSchema.enumValues) {
    failures.push(`${nullableName}: 기반 schema와 nullable 변형 모두 inline enum이어야 합니다.`);
    continue;
  }

  const expected = [...baseSchema.enumValues, 'null'].sort();
  const actual = [...nullableSchema.enumValues].sort();
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    failures.push(
      `${nullableName}: enum은 ${baseName} 값 집합 뒤에 null을 추가한 ${JSON.stringify(expected)}여야 합니다.`,
    );
  }
}

for (const [ownerName, fieldName, targetName] of nullableFieldRefs) {
  const ownerSchema = schemas.get(ownerName);
  if (!ownerSchema) {
    failures.push(`${ownerName}: response schema가 없습니다.`);
    continue;
  }

  const expectedRef = `${fieldName}: { $ref: '#/components/schemas/${targetName}' }`;
  if (!ownerSchema.lines.some((line) => line.trim() === expectedRef)) {
    failures.push(`${ownerName}.${fieldName}: ${targetName} schema를 직접 참조해야 합니다.`);
  }
}

if (!schemas.get('QualityGateRes')?.nullable) {
  failures.push('QualityGateRes: nullable: true가 필요합니다.');
}

const comparisonSchema = schemas.get('TestRunComparisonRes');
const comparisonSummarySchema = schemas.get('TestRunComparisonSummaryRes');
if (!comparisonSchema || !comparisonSummarySchema) {
  failures.push('TestRun comparison 전체/요약 schema가 모두 필요합니다.');
} else {
  for (const fieldName of comparisonSummaryFields) {
    const propertyPattern = new RegExp(`^        ${fieldName}:`);
    const comparisonProperty = comparisonSchema.lines.find((line) => propertyPattern.test(line));
    const summaryProperty = comparisonSummarySchema.lines.find((line) => propertyPattern.test(line));
    if (!comparisonProperty || !summaryProperty || comparisonProperty.trim() !== summaryProperty.trim()) {
      failures.push(`TestRunComparisonSummaryRes.${fieldName}: 전체 comparison과 같은 정의가 필요합니다.`);
    }
    const requiredLine = `        - ${fieldName}`;
    if (!comparisonSchema.lines.includes(requiredLine) || !comparisonSummarySchema.lines.includes(requiredLine)) {
      failures.push(`TestRunComparisonSummaryRes.${fieldName}: 두 schema의 required 필드여야 합니다.`);
    }
  }
  if (comparisonSummarySchema.lines.some((line) => /^        items:/.test(line))) {
    failures.push('TestRunComparisonSummaryRes: case-level items를 포함할 수 없습니다.');
  }
}

if (failures.length > 0) {
  console.error(failures.join('\n'));
  process.exit(1);
}

console.log(
  `${nullableSchemas.length}개 Nullable* enum, ${nullableFieldRefs.length}개 nullable response field 참조, comparison summary 공통 필드를 확인했습니다.`,
);
