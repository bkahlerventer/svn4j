/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.cli.command;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author TMate Software Ltd.
 */
public class MkDirCommand extends SVNCommand {

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasURLs()) {
            createLocalDirectories(out, err);
        } else {
            createRemoteDirectories(out, err);
        }
    }

    private void createLocalDirectories(final PrintStream out, PrintStream err) {
        final Collection paths = new ArrayList();
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            if (matchTabsInPath(getCommandLine().getPathAt(i), err)) {
                continue;
            }
            paths.add(new File(getCommandLine().getPathAt(i)));
        }
        if (paths.isEmpty()) {
            return;
        }
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNWCClient wcClient = getClientManager().getWCClient();
        boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
        for (Iterator files = paths.iterator(); files.hasNext();) {
            File file = (File) files.next();
            try {
                wcClient.doAdd(file, false, true, false, recursive);
            } catch (SVNException e) {
                err.println(e.getMessage());
            }
        }
    }

    private void createRemoteDirectories(final PrintStream out, PrintStream err) throws SVNException {
        final Collection urls = new ArrayList();
        for (int i = 0; i < getCommandLine().getURLCount(); i++) {
            if (matchTabsInURL(getCommandLine().getURL(i), err)) {
                continue;
            }
            urls.add(getCommandLine().getURL(i));
        }
        if (urls.isEmpty()) {
            return;
        }
        String message = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNCommitClient client = getClientManager().getCommitClient();
        String[] paths = (String[]) urls.toArray(new String[urls.size()]);
        SVNURL[] svnURLs = new SVNURL[paths.length];
        for (int i = 0; i < svnURLs.length; i++) {
            svnURLs[i] = SVNURL.parseURIEncoded(paths[i]);
        }
        SVNCommitInfo info = client.doMkDir(svnURLs, message == null ? "" : message);
        if (info != SVNCommitInfo.NULL) {
            out.println();
            out.println("Committed revision " + info.getNewRevision() + ".");
        }
    }
}
