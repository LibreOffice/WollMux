/*-
 * #%L
 * WollMux
 * %%
 * Copyright (C) 2005 - 2024 Landeshauptstadt München and LibreOffice contributors
 * %%
 * Licensed under the EUPL, Version 1.1 or – as soon they will be
 * approved by the European Commission - subsequent versions of the
 * EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl5
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 * #L%
 */
package org.libreoffice.lots.form.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.libreoffice.lots.config.ConfigThingy;
import org.libreoffice.lots.form.config.VisibilityGroupConfig;

public class TestVisibilityGroupConfig
{

  @Test
  public void testVisibilityConfig() throws Exception
  {
    ConfigThingy conf = new ConfigThingy("Test", "\"true\"");
    VisibilityGroupConfig group = new VisibilityGroupConfig(conf);
    assertEquals("Test", group.getGroupId());
    assertEquals(conf, group.getCondition());
  }

}
