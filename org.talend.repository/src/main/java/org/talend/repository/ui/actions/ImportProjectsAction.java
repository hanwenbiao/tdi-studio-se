// ============================================================================
//
// Talend Community Edition
//
// Copyright (C) 2006 Talend - www.talend.com
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
//
// ============================================================================
package org.talend.repository.ui.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.wizards.datatransfer.ExternalProjectImportWizard;
import org.talend.core.ui.images.ImageProvider;
import org.talend.repository.ui.ERepositoryImages;

/**
 * Action used to refresh a repository view.<br/>
 * 
 * $Id: RefreshAction.java 824 2006-12-01 15:49:55 +0000 (ven., 01 déc. 2006) smallet $
 * 
 */
public final class ImportProjectsAction extends Action {

    private static final String IMPORT_PROJECTS = "Import projects";

    private static ImportProjectsAction singleton;

    public static ImportProjectsAction getInstance() {
        if (singleton == null) {
            singleton = new ImportProjectsAction();
        }
        return singleton;
    }

    private ImportProjectsAction() {
        super();
        this.setText(IMPORT_PROJECTS);
        this.setToolTipText(IMPORT_PROJECTS);
        this.setImageDescriptor(ImageProvider.getImageDesc(ERepositoryImages.IMPORT_PROJECTS_ACTION));
    }

    public void run() {
        ExternalProjectImportWizard processWizard = new ExternalProjectImportWizard();
        Shell activeShell = Display.getCurrent().getActiveShell();
        WizardDialog dialog = new WizardDialog(activeShell, processWizard);
        processWizard.setWindowTitle(IMPORT_PROJECTS);
        dialog.create();
        dialog.open();
    }
}
