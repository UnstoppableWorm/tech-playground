# Project instructions

## Velog development records

- When the user asks to document, blog, or publish work from this repository, use the configured `velog` MCP server.
- Base the article on inspected source changes, commits, and test results. Do not invent implementation details or verification results.
- Create a Velog draft first with `velog_create_draft` and return its title and identifier to the user.
- Do not call `velog_publish_draft` unless the user explicitly asks to publish and specifies whether the post should be private or public.
- Never read, print, copy, or commit Velog token files or token values.
