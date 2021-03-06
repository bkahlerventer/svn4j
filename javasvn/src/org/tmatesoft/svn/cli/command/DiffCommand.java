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

import java.io.File;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @author TMate Software Ltd.
 */
public class DiffCommand extends SVNCommand {

    public void run(final PrintStream out, PrintStream err) throws SVNException {
        boolean error = false;
        SVNDiffClient differ = getClientManager().getDiffClient();
        File userDir = new File(".").getAbsoluteFile().getParentFile();
        differ.getDiffGenerator().setBasePath(userDir);

        boolean useAncestry = getCommandLine().hasArgument(SVNArgument.USE_ANCESTRY);
        boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
        differ.getDiffGenerator().setDiffDeleted(!getCommandLine().hasArgument(SVNArgument.NO_DIFF_DELETED));
        differ.getDiffGenerator().setForcedBinaryDiff(getCommandLine().hasArgument(SVNArgument.FORCE));

        if (getCommandLine().getURLCount() == 2 && !getCommandLine().hasPaths()) {
            // diff url1[@r] url2[@r] (case 3)
            SVNURL url1 = SVNURL.parseURIEncoded(getCommandLine().getURL(0));
            SVNURL url2 = SVNURL.parseURIEncoded(getCommandLine().getURL(1));
            SVNRevision peg1 = getCommandLine().getPegRevision(0);
            SVNRevision peg2 = getCommandLine().getPegRevision(1);
            if (peg1 == SVNRevision.UNDEFINED) {
                peg1 = SVNRevision.HEAD;
            }
            if (peg2 == SVNRevision.UNDEFINED) {
                peg2 = SVNRevision.HEAD;
            }

            differ.doDiff(url1, peg1, url2, peg2, recursive, useAncestry, out);
        } else {
            SVNRevision rN = SVNRevision.UNDEFINED;
            SVNRevision rM = SVNRevision.UNDEFINED;
            String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
            if (revStr != null && revStr.indexOf(':') > 0) {
                rN = SVNRevision.parse(revStr.substring(0, revStr.indexOf(':')));
                rM = SVNRevision.parse(revStr.substring(revStr.indexOf(':') + 1));
            } else if (revStr != null) {
                rN = SVNRevision.parse(revStr);
            }
            if (getCommandLine().hasArgument(SVNArgument.OLD)) {
                // diff [-rN[:M]] --old=url[@r] [--new=url[@r]] [path...] (case2)
                String oldPath = (String) getCommandLine().getArgumentValue(SVNArgument.OLD);
                String newPath = (String) getCommandLine().getArgumentValue(SVNArgument.NEW);
                if (newPath == null) {
                    newPath = oldPath;
                }
                if (oldPath.startsWith("=")) {
                    oldPath = oldPath.substring(1);
                }
                if (newPath.startsWith("=")) {
                    newPath = newPath.substring(1);
                }
                if (oldPath.indexOf('@') > 0) {
                    rN = SVNRevision.parse(oldPath.substring(oldPath.lastIndexOf('@') + 1));
                    oldPath = oldPath.substring(0, oldPath.lastIndexOf('@'));
                }
                if (newPath.indexOf('@') > 0) {
                    rM = SVNRevision.parse(newPath.substring(newPath.lastIndexOf('@') + 1));
                    newPath = newPath.substring(0, newPath.lastIndexOf('@'));
                }
                if (getCommandLine().getPathCount() == 0) {
                    getCommandLine().setPathAt(0, "");
                }
                if (rN == SVNRevision.UNDEFINED) {
                    rN = getCommandLine().isURL(oldPath) ? SVNRevision.HEAD : SVNRevision.BASE;
                }
                if (rM == SVNRevision.UNDEFINED) {
                    rM = getCommandLine().isURL(newPath) ? SVNRevision.HEAD : SVNRevision.WORKING;
                }
                
                for (int i = 0; i < getCommandLine().getPathCount(); i++) {
                    String p = getCommandLine().getPathAt(i);
                    p = p.replace(File.separatorChar, '/');
                    if (".".equals(p)) {
                        p = "";
                    }
                    String oP = SVNPathUtil.append(oldPath, p);
                    String nP = SVNPathUtil.append(newPath, p);
                    try {
                        if (!getCommandLine().isURL(oP) && getCommandLine().isURL(nP)) {
                            File path1 = new File(oP).getAbsoluteFile();
                            SVNURL url2 = SVNURL.parseURIEncoded(nP);
                            // path:url
                            differ.doDiff(path1, rN, url2, rM, recursive, useAncestry, out);
                        } else if (getCommandLine().isURL(oP) && !getCommandLine().isURL(nP)) {
                            // url:path
                            File path2 = new File(nP).getAbsoluteFile();
                            SVNURL url1 = SVNURL.parseURIEncoded(oP);
                            differ.doDiff(url1, rN, path2, rM, recursive, useAncestry, out);
                        } else if (getCommandLine().isURL(oP) && getCommandLine().isURL(nP)) {
                            // url:url
                            SVNURL url1 = SVNURL.parseURIEncoded(oP);
                            SVNURL url2 = SVNURL.parseURIEncoded(nP);
                            differ.doDiff(url1, rN, url2, rM, recursive, useAncestry, out);
                        } else {
                            // path:path
                            File path1 = new File(oP).getAbsoluteFile();
                            File path2 = new File(nP).getAbsoluteFile();
                            differ.doDiff(path1, rN, path2, rM, recursive, useAncestry, out);
                        }
                    } catch (SVNException e) {
                        SVNDebugLog.logInfo(e);
                        error = true;
                        println(err, e.getMessage());
                    }
                }
            } else {
                // diff [-rN[:M]] target[@r] [...] (case1)
                SVNRevision r1 = rN;
                SVNRevision r2 = rM;
                r1 = r1 == SVNRevision.UNDEFINED ? SVNRevision.BASE : r1;
                r2 = r2 == SVNRevision.UNDEFINED ? SVNRevision.WORKING : r2;
                boolean peggedDiff = r1 != SVNRevision.WORKING && r1 != SVNRevision.BASE;
                
                for(int i = 0; i < getCommandLine().getPathCount(); i++) {
                    String path = getCommandLine().getPathAt(i);
                    File path1 = new File(path).getAbsoluteFile();
                    if (peggedDiff) {
                        SVNRevision peg = getCommandLine().getPathPegRevision(i);
                        peg = peg == SVNRevision.UNDEFINED ? SVNRevision.WORKING : peg;
                        differ.doDiff(path1, peg, r1, r2, recursive, useAncestry, out);
                    } else {
                        differ.doDiff(path1, r1, path1, r2, recursive, useAncestry, out);
                    }
                }
                r1 = rN;
                r2 = rM;
                peggedDiff = r1 != SVNRevision.WORKING && r1 != SVNRevision.BASE;
                r2 = r2 == SVNRevision.UNDEFINED ? SVNRevision.HEAD : r2;
                
                for(int i = 0; i < getCommandLine().getURLCount(); i++) {
                    String url = getCommandLine().getURL(i);
                    SVNURL url1 = SVNURL.parseURIEncoded(url);
                    if (peggedDiff) {
                        SVNRevision peg = getCommandLine().getPegRevision(i);
                        peg = peg == SVNRevision.UNDEFINED ? SVNRevision.HEAD : peg;
                        differ.doDiff(url1, peg, r1, r2, recursive, useAncestry, out);
                    } else {
                        differ.doDiff(url1, r1, url1, r2, recursive, useAncestry, out);
                    }
                }
            }
        }
        if (error) {
            System.exit(1);
        }
    }
}
