/*
 * Dateiname: TestHandler.java
 * Projekt  : WollMux
 * Funktion : Enthält die DispatchHandler für alle dispatch-Urls, die
 *            mit "wollmux:Test" anfangen
 * 
 * Copyright (c) 2008-2018 Landeshauptstadt München
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the European Union Public Licence (EUPL),
 * version 1.0 (or any later version).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * European Union Public Licence for more details.
 *
 * You should have received a copy of the European Union Public Licence
 * along with this program. If not, see
 * http://ec.europa.eu/idabc/en/document/7330
 *
 * Änderungshistorie:
 * Datum      | Wer | Änderungsgrund
 * -------------------------------------------------------------------
 * 07.05.2007 | LUT | Erstellung als TestHandler.java
 * -------------------------------------------------------------------
 *
 * @author Christoph Lutz (D-III-ITD 5.1)
 * @version 1.0
 * 
 */
package de.muenchen.allg.itd51.wollmux.event;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.muenchen.allg.itd51.wollmux.SachleitendeVerfuegung;
import de.muenchen.allg.itd51.wollmux.XPrintModel;
import de.muenchen.allg.itd51.wollmux.core.parser.ConfigThingy;
import de.muenchen.allg.itd51.wollmux.core.util.L;
import de.muenchen.allg.itd51.wollmux.dialog.SachleitendeVerfuegungenDruckdialog.VerfuegungspunktInfo;
import de.muenchen.allg.itd51.wollmux.document.TextDocumentController;
import de.muenchen.allg.itd51.wollmux.func.StandardPrint;
import de.muenchen.allg.itd51.wollmux.print.PrintModels;

/**
 * Enthält die DispatchHandler für alle dispatch-Urls, die mit "wollmux:Test"
 * anfangen und für den automatisierten Test durch wollmux-qatest benötigt werden.
 * Der Handler wird nur installiert, wenn die wollmux.conf die Direktive
 * QA_TEST_HANDLER "true" enthält.
 *
 * @author Christoph Lutz (D-III-ITD-5.1)
 */
public class TestHandler
{

  private static final Logger LOGGER = LoggerFactory
      .getLogger(TestHandler.class);

  /**
   * Dieses File enthält die Argumente, die einem TestHandler übergeben werden sollen
   * und vor dem Aufruf des Teshandlers über das testtool geschrieben wurden.
   */
  private static final File WOLLMUX_QATEST_ARGS_FILE =
    new File("/tmp/wollmux_qatest.args");

  /**
   * Bearbeitet den Test, der im Argument arg spezifiziert ist und im
   * TextDocumentModel model ausgeführt werden soll.
   * 
   * @param documentController
   * @param arg
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  public static void doTest(TextDocumentController documentController, String arg)
  {
    String[] args = arg.split("#");
    String cmd = args[0];

    /** ************************************************************** */
    if (cmd.equalsIgnoreCase("VerfuegungspunktDrucken"))
    {
      Map<String, String> idsAndValues = getWollmuxTestArgs();
      int count = SachleitendeVerfuegung.countVerfuegungspunkte(documentController.getModel().doc);
      int verfPunkt = Short.parseShort(idsAndValues.get("VerfPunkt"));
      boolean isDraft = (verfPunkt == count) ? true : false;
      boolean isOriginal = (verfPunkt == 1) ? true : false;
      final XPrintModel pmod = PrintModels.createPrintModel(documentController, false);
      try
      {
        List<VerfuegungspunktInfo> settings = new ArrayList<VerfuegungspunktInfo>();
        settings.add(new VerfuegungspunktInfo(verfPunkt, (short) 1, isDraft,
          isOriginal));
        pmod.setPropertyValue(StandardPrint.PROP_SLV_SETTINGS, settings);
      }
      catch (Exception e)
      {
        LOGGER.error("", e);
      }
      // FIXME: auskommentiert --> Code broken
      // ((InternalPrintModel)
      // pmod).useInternalPrintFunction(StandardPrint.getInternalPrintFunction(
      // "sachleitendeVerfuegungOutput", 10));
      // Drucken im Hintergrund, damit der WollMux weiterläuft.
      new Thread()
      {
        @Override
        public void run()
        {
          pmod.printWithProps();
        }
      }.start();
    }

    /** ************************************************************** */
    if (cmd.equalsIgnoreCase("SchreibeFormularwerte"))
    {
      Map<String, String> idsAndValues = getWollmuxTestArgs();
      for (Map.Entry<String, String> ent : idsAndValues.entrySet())
      {
        String id = ent.getKey();
        String value = ent.getValue();
        WollMuxEventHandler.getInstance().handleSetFormValue(documentController.getModel().doc, id, value, null);
      }
    }

    /** ************************************************************** */
    if (cmd.equalsIgnoreCase("EinTest"))
    {
      ConfigThingy c = new ConfigThingy("Funktion");
      c.addChild(new ConfigThingy("Hallo"));
      ConfigThingy trafo = documentController.getModel().getFormFieldTrafoFromSelection();
      LOGGER.error("EinTest Trafo = '"
        + ((trafo != null) ? trafo.stringRepresentation() : "null") + "'");
      if (trafo != null)
      {
        try
        {
          documentController.setTrafo(trafo.getName(), c);
        }
        catch (Exception e)
        {
          LOGGER.error("", e);
        }
      }
    }
  }

  /**
   * Liest die Argumente aus der Datei WOLLMUX_QATEST_ARGS_FILE in eine HashMap ein
   * und liefert diese zurück. Die Argumente werden in der Datei in Zeilen der Form
   * "<key>,<value>" abgelegt erwartet (key darf dabei kein "," enthalten).
   * 
   * @return
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  private static HashMap<String, String> getWollmuxTestArgs()
  {
    HashMap<String, String> args = new HashMap<>();
    
    try (BufferedReader br = new BufferedReader(new FileReader(WOLLMUX_QATEST_ARGS_FILE)))
    {
      for (String line = null; (line = br.readLine()) != null;)
      {
        String[] keyValue = line.split(",", 2);
        args.put(keyValue[0], keyValue[1]);
      }
    }
    catch (Exception e)
    {
      LOGGER.error(
        L.m("Argumentdatei für wollmux-qatest konnte nicht gelesen werden"), e);
    }
    
    return args;
  }
}
