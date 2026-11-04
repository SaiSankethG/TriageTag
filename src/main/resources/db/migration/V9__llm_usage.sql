-- V9: LLM tokens used per day (M4 step 7, cost guard). Every LLM call adds to today's row.
CREATE TABLE llm_usage (
    day    date    PRIMARY KEY,   -- UTC day
    tokens bigint  NOT NULL,      -- input + output tokens, all calls of that day
    calls  integer NOT NULL
);
