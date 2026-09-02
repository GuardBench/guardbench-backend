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
    currentSchema = { nullable: false, enumValues: undefined };
    schemas.set(schemaMatch[1], currentSchema);
    continue;
  }
  if (!currentSchema) continue;
  if (/^      nullable: true\s*$/.test(line)) currentSchema.nullable = true;

  const enumMatch = line.match(/^      enum: \[(.*)]\s*$/);
  if (enumMatch) {
    currentSchema.enumValues = enumMatch[1].split(',').map((value) => value.trim());
  }
}

const failures = [];
const nullableSchemas = [...schemas.entries()].filter(([name]) => name.startsWith('Nullable'));

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

if (failures.length > 0) {
  console.error(failures.join('\n'));
  process.exit(1);
}

console.log(`${nullableSchemas.length}개 Nullable* enum schema의 기반 값 집합과 null 멤버를 확인했습니다.`);
