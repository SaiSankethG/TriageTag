package com.saisanketh.triage.docs;

import java.util.List;

/** Reads documentation files from a repo at a fixed tag. */
public interface DocSource {

    /** Paths of all files in the repo at that tag (GitHub's tree is one request for the whole repo). */
    List<String> listFiles(String repo, String tag);

    /** The file's text. */
    String read(String repo, String tag, String path);
}
