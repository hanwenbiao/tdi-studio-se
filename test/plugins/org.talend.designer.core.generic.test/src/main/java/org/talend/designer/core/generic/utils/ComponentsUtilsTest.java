// ============================================================================
//
// Copyright (C) 2006-2016 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.designer.core.generic.utils;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;
import org.talend.components.api.properties.ComponentProperties;
import org.talend.core.model.process.Element;
import org.talend.daikon.properties.presentation.Form;
import org.talend.designer.core.model.FakeElement;
import org.talend.designer.core.model.components.ElementParameter;
import org.talend.test.utils.testproperties.TestProperties;

/**
 * created by hcyi on Feb 16, 2016 Detailled comment
 *
 */
public class ComponentsUtilsTest {

    @Test
    public void testGetParametersFromForm() {
        ComponentProperties props = (ComponentProperties) new TestProperties("test").init(); //$NON-NLS-1$
        Form form = props.getForm(Form.MAIN);

        /*
         * Test wizard
         */
        Element element = new FakeElement(form.getName());

        // Test readonly case
        List<ElementParameter> parameters = ComponentsUtils.getParametersFromForm(element, null, props, form);
        for (ElementParameter parameter : parameters) {
            assertFalse(parameter.isReadOnly());
        }

        element.setReadOnly(true);
        parameters = ComponentsUtils.getParametersFromForm(element, null, props, form);
        for (ElementParameter parameter : parameters) {
            assertTrue(parameter.isReadOnly());
        }

    }

}
