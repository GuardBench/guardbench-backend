---
name: jqassistant-code-graph
description: Use the jQAssistant Neo4j graph to discover and narrow Java types, methods, callers, dependencies, and nearby code, then verify every relevant finding against source files. Use for structural codebase questions; do not treat static graph paths as runtime traces.
---

# jQAssistant Java code graph

Use the repository's `jqassistant_graph` MCP server as a structural index for Java bytecode. The graph helps locate relevant code; source files remain authoritative for current behavior and exact implementation.

## Local MCP setup

The repository does not own or manage personal Codex configuration. Each developer adds the following MCP block to their repository-root .codex/config.toml. The .codex/ directory is ignored by Git. Merge this entry into an existing personal config; never overwrite or reset that file.

~~~toml
[mcp_servers.jqassistant_graph]
command = "neo4j-mcp"
startup_timeout_sec = 30
enabled_tools = ["get-schema", "read-cypher"]
env = { NEO4J_MCP_READ_ONLY = "true", NEO4J_MCP_TELEMETRY = "false" }
env_vars = [
  "NEO4J_MCP_URI",
  "NEO4J_MCP_USERNAME",
  "NEO4J_MCP_PASSWORD",
  "NEO4J_MCP_DATABASE"
]
~~~

Install neo4j-mcp and ensure it is on PATH. Set the four connection variables in the environment that starts Codex; env_vars forwards those local values and does not load a .env file. The URI includes the Neo4j host and port. The allowlist exposes schema and read-only Cypher tools only. Keep credentials out of repository files. The project does not require the global ~/.codex/config.toml.

## Workflow

1. Call `jqassistant_graph.get-schema` first when the graph schema is unknown or may have changed. Confirm labels, relationship directions, and properties instead of assuming them.
2. Start from a specific fully qualified type, method, package, or symbol from the question. Use bounded `jqassistant_graph.read-cypher` queries to find likely types, methods, dependencies, callers, or a small neighborhood. Prefer exact parameters and `LIMIT`; keep variable-length traversals shallow (usually one or two hops).
3. Convert graph hits to source paths. Search with `rg` and read the matching source and relevant callers/callees. Confirm signatures, guards, configuration, and actual control flow in source before making claims.
4. Use `git log`/`git blame` only when the question concerns when or why behavior changed, or source alone cannot establish history. History is evidence of change, not proof of current runtime behavior.
5. Report which graph queries were used, which source files were checked, and any mismatch or missing graph/source evidence.

## Common graph traversals

Check schema first. In the current jQAssistant model, Java bytecode types and methods commonly carry both `Java` and their kind labels.

Declared methods of a type:

```cypher
MATCH (t:Java:Type {fqn: $type})-[:DECLARES]->(m:Java:Method)
RETURN t.fqn AS type, m.name AS method, m.signature AS signature
ORDER BY method
LIMIT 100
```

Outgoing type dependencies (`DEPENDS_ON`):

```cypher
MATCH (t:Java:Type {fqn: $type})-[:DEPENDS_ON]->(dependency:Java:Type)
RETURN dependency.fqn AS dependency
ORDER BY dependency
LIMIT 100
```

Reverse dependencies (types that depend on the selected type):

```cypher
MATCH (dependent:Java:Type)-[:DEPENDS_ON]->(t:Java:Type {fqn: $type})
RETURN dependent.fqn AS dependent
ORDER BY dependent
LIMIT 100
```

Invoked methods and reverse callers:

```cypher
MATCH (caller:Java:Method)-[:INVOKES]->(callee:Java:Method)
WHERE caller.name = $callerName AND callee.name = $calleeName
RETURN caller.name AS caller, caller.signature AS callerSignature,
       callee.name AS callee, callee.signature AS calleeSignature
LIMIT 100
```

```cypher
MATCH (caller:Java:Method)-[:INVOKES]->(callee:Java:Method)
WHERE callee.name = $methodName
RETURN caller.name AS caller, caller.signature AS callerSignature,
       callee.name AS callee, callee.signature AS calleeSignature
LIMIT 100
```

For a one-hop neighborhood around a known type, use an exact seed and cap results:

```cypher
MATCH (seed:Java:Type {fqn: $type})-[r]-(neighbor)
RETURN type(r) AS relationship, labels(neighbor) AS labels,
       neighbor.fqn AS fqn, neighbor.name AS name
LIMIT 100
```

Only expand to a variable-length neighborhood when a direct query is insufficient; constrain the seed, depth, and result count. A `LIMIT` bounds returned rows but not necessarily traversal work, so avoid unanchored or all-pairs queries.

## Choosing evidence

- **Graph:** structural questions—declares, invokes, dependencies, reverse dependents/callers, inheritance, and candidate neighborhoods. Graph results narrow the search; they are not source-level proof.
- **`rg`:** locate symbol references, annotations, configuration keys, tests, and source paths; check graph hits against the working tree.
- **Source files:** authoritative for current behavior, conditions, transaction boundaries, configuration binding, and exact execution flow.
- **Git history:** explain when/why a line or behavior changed; use only after identifying the relevant current source.

## Limits and safety

- `INVOKES` and `DEPENDS_ON` describe static bytecode relationships. They do not prove a particular request executed that path, which runtime implementation Spring selected, or which profile/property was active.
- Compiled bytecode can contain generated/synthetic members and external or unresolved types. Verify project-owned candidates in source and distinguish external dependencies.
- Do not infer active Spring beans, profile selection, runtime order, or production traffic from this graph. Use runtime traces/configuration evidence for those questions.
- Use only the configured read-only tools `get-schema` and `read-cypher`. Never request or expose credentials. The connection is supplied by `NEO4J_MCP_URI`, `NEO4J_MCP_USERNAME`, `NEO4J_MCP_PASSWORD`, and `NEO4J_MCP_DATABASE`; URI carries host and port. If a required variable or database is unavailable, report that exact requirement rather than inventing a value or switching databases.
