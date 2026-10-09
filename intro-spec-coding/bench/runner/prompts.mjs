// The two fixed entry prompts (思路 §十二、§十三). Every run of a group gets exactly the same text.
export const NATIVE_PROMPT = `Implement the requirements defined in TASK.md.

You may inspect and modify the repository and run existing development commands.

Do not modify TASK.md.
Do not modify benchmark tests.

When you believe the task is complete, stop.`;

export const OPENSPEC_PROMPT = `Use OpenSpec to implement the requirements described in TASK.md.

Follow the complete OpenSpec workflow: proposal/spec/design/tasks/apply.

Do not alter the original requirements.
Do not modify TASK.md.
Do not modify benchmark tests.

Stop when implementation is complete.`;
