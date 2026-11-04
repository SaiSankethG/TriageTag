-- V6: how a closed issue was fixed (M4 step 3, "already fixed?" hint). Filled on demand from GitHub's records,
-- once per issue, so the hint doesn't call GitHub again for the same match.
CREATE TABLE issue_fix (
    issue_id      bigint       PRIMARY KEY REFERENCES issue (id) ON DELETE CASCADE,
    closer_type   varchar(20),            -- 'PullRequest' or 'Commit'; null if GitHub doesn't say what closed it
    closer_number integer,                -- the pull request's number (null for a commit)
    closer_url    text,                   -- link to the PR or commit
    fix_version   varchar(100),           -- the PR's milestone, else the issue's milestone, e.g. '3.4.2'
    fetched_at    timestamptz  NOT NULL
);
