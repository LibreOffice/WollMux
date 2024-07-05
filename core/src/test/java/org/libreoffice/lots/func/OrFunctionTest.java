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
package org.libreoffice.lots.func;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.libreoffice.lots.config.ConfigThingy;
import org.libreoffice.lots.dialog.DialogLibrary;
import org.libreoffice.lots.func.Function;
import org.libreoffice.lots.func.FunctionLibrary;
import org.libreoffice.lots.func.OrFunction;
import org.libreoffice.lots.func.StringLiteralFunction;

public class OrFunctionTest
{

  @Test
  public void orFunction() throws Exception
  {
    Function f = new OrFunction(new ConfigThingy("OR", "\"false\" \"true\""), new FunctionLibrary(),
        new DialogLibrary(), new HashMap<>());
    assertEquals(0, f.parameters().length);
    assertEquals("true", f.getResult(null));
    assertTrue(f.getBoolean(null));
    Collection<String> dialogFunctions = new ArrayList<>();
    f.getFunctionDialogReferences(dialogFunctions);
    assertTrue(dialogFunctions.isEmpty());

    f = new OrFunction(List.of(new StringLiteralFunction("false")));
    assertEquals("false", f.getResult(null));
    assertFalse(f.getBoolean(null));

    f = new OrFunction(List.of(new StringLiteralFunction(FunctionLibrary.ERROR)));
    assertEquals(FunctionLibrary.ERROR, f.getResult(null));
  }

}
