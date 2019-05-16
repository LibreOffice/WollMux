/*
 * Dateiname: MailMergeNew.java
 * Projekt  : WollMux
 * Funktion : Die neuen erweiterten Serienbrief-Funktionalitäten
 * 
 * Copyright (c) 2008-2019 Landeshauptstadt München
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
 * 04.03.2008 | BNK | Herausfaktorisiert aus MailMergeNew
 * 23.10.2013 | JGM | FilePicker durch Swing FileChooser ersetzt (Loest Probleme mit LO 4)
 * 20.11.2013 | UKT | Swing FileChooser wieder durch LO FilePicker ersetzt
 * -------------------------------------------------------------------
 *
 * @author Matthias Benkmann (D-III-ITD 5.1)
 * @version 1.0
 *
 */
package de.muenchen.allg.itd51.wollmux.dialog.mailmerge;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.star.beans.XPropertySet;
import com.sun.star.container.XEnumeration;
import com.sun.star.container.XNameAccess;
import com.sun.star.lang.DisposedException;
import com.sun.star.sdbc.XConnection;
import com.sun.star.sdbc.XDataSource;
import com.sun.star.sdbc.XRow;
import com.sun.star.sdbc.XRowSet;
import com.sun.star.sdbcx.XColumnsSupplier;
import com.sun.star.sheet.XCellRangesQuery;
import com.sun.star.sheet.XSheetCellRanges;
import com.sun.star.sheet.XSpreadsheetDocument;
import com.sun.star.sheet.XSpreadsheets;
import com.sun.star.table.CellRangeAddress;
import com.sun.star.table.XCellRange;
import com.sun.star.ui.dialogs.XFilePicker;
import com.sun.star.uno.UnoRuntime;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.afid.UnoHelperException;
import de.muenchen.allg.itd51.wollmux.core.db.ColumnNotFoundException;
import de.muenchen.allg.itd51.wollmux.core.db.Dataset;
import de.muenchen.allg.itd51.wollmux.core.db.Datasource;
import de.muenchen.allg.itd51.wollmux.core.db.OOoDatasource;
import de.muenchen.allg.itd51.wollmux.core.db.QueryResults;
import de.muenchen.allg.itd51.wollmux.core.db.QueryResultsWithSchema;
import de.muenchen.allg.itd51.wollmux.core.db.TimeoutException;
import de.muenchen.allg.itd51.wollmux.core.document.TextDocumentModel.FieldSubstitution;
import de.muenchen.allg.itd51.wollmux.core.exceptions.UnavailableException;
import de.muenchen.allg.itd51.wollmux.core.parser.ConfigThingy;
import de.muenchen.allg.itd51.wollmux.core.parser.NodeNotFoundException;
import de.muenchen.allg.itd51.wollmux.core.util.L;
import de.muenchen.allg.itd51.wollmux.dialog.trafo.TrafoDialogParameters;
import de.muenchen.allg.itd51.wollmux.document.TextDocumentController;
import de.muenchen.allg.itd51.wollmux.event.WollMuxEventHandler;

/**
 * Stellt eine OOo-Datenquelle oder ein offenes Calc-Dokument über ein gemeinsames Interface zur
 * Verfügung. Ist auch zuständig dafür, das Calc-Dokument falls nötig wieder zu öffnen und
 * Änderungen seines Fenstertitels und/oder seiner Speicherstelle zu überwachen. Stellt auch Dialoge
 * zur Verfügung zur Auswahl der Datenquelle.
 *
 * @author Matthias Benkmann (D-III-ITD 5.1)
 * @author Christoph Lutz
 * @author Daniel Sikeler
 * @author Björn Ranft
 */
public class MailMergeDatasource
{

  private static final Logger LOGGER = LoggerFactory.getLogger(MailMergeDatasource.class);

  public enum SOURCE_TYPE
  {
    NONE,
    CALC,
    DB,
  }

  private SOURCE_TYPE currentSourceType = SOURCE_TYPE.NONE;

  /**
   * Wenn nach dieser Zeit in ms nicht alle Daten des Seriendruckauftrags ausgelesen werden konnten,
   * dann wird der Druckauftrag nicht ausgeführt (und muss eventuell über die Von Bis Auswahl in
   * mehrere Aufträge zerteilt werden).
   */
  private static final long MAILMERGE_GETCONTENTS_TIMEOUT = 60000;

  /**
   * Timeout für den Login bei einer OOo-Datenquelle.
   */
  private static final long MAILMERGE_LOGIN_TIMEOUT = 5000;

  /**
   * Ein String der möglichst nie in einem vom Benutzer eingegebenen Felder-Anpassen Feld auftauchen
   * sollte. Wird als Platzhalter für das Einfügen der Zeilennummer in den Formel-String verwendet.
   * Darf keine Zeichen enthalten, die in regulären Ausdrücken Bedeutung haben.
   */
  private static final String ROW_NUM_PLACEHOLDER = "Ø©¿";

  private int previewDatasetNumber = 1;

  /**
   * Wenn {@link #sourceType} == {@link #CALC} und das Calc-Dokument derzeit offen ist, dann ist
   * diese Variable != null. Falls das Dokument nicht offen ist, so ist seine URL in
   * {@link #calcUrl} zu finden. Die Kombination calcDoc == null && calcUrl == null && sourceType ==
   * SOURCE_CALC ist unzulässig.
   */
  private XSpreadsheetDocument selectedCalcDoc = null;

  /**
   * Wenn {@link #sourceType} == {@link #CALC} und das Calc-Dokument bereits einmal gespeichert
   * wurde, findet sich hier die URL des Dokuments, ansonsten ist der Wert null. Falls das Dokument
   * nur als UnbenanntX im Speicher existiert, so ist eine Referenz auf das Dokument in
   * {@link #calcDoc} zu finden. Die Kombination calcDoc == null && calcUrl == null && sourceType ==
   * SOURCE_CALC ist unzulässig.
   */
  private String calcUrl = null;

  /**
   * Falls {@link #sourceType} == {@link #DB}, so ist dies der Name der ausgewählten
   * OOo-Datenquelle, ansonsten null.
   */
  //private String oooDatasourceName = null;

  /**
   * Falls {@link #sourceType} == {@link #DB} und die Datenquelle bereits initialisiert wurde (durch
   * {@link #getOOoDatasource()}), so ist dies eine {@link Datasource} zum Zugriff auf die
   * ausgewählte OOo-Datenquelle, ansonsten null.
   */
  private Datasource oooDatasource = null;

  /**
   * Speichert den Namen der Tabelle bzw, des Tabellenblattes, die als Quelle der Serienbriefdaten
   * ausgewählt wurde. Ist niemals null, kann aber der leere String sein oder ein Name, der gar
   * nicht in der entsprechenden Datenquelle existiert.
   */
  private String tableName = "";

  /**
   * Wird verwendet zum Speichern/Wiedereinlesen der zuletzt ausgewählten Datenquelle.
   */
  private TextDocumentController documentController;

  /**
   * Erzeugt eine neue Datenquelle.
   *
   * @param documentController
   *          wird verwendet zum Speichern/Wiedereinlesen der zuletzt ausgewählten Datenquelle.
   * @author Matthias Benkmann (D-III-ITD 5.1)
   */
  public MailMergeDatasource(TextDocumentController documentController)
  {
    this.documentController = documentController;
    openDatasourceFromLastStoredSettings();
  }

  /**
   * Liefert die Titel der Spalten der aktuell ausgewählten Tabelle. Ist derzeit keine Tabelle
   * ausgewählt oder enthält die ausgewählte Tabelle keine benannten Spalten, so wird ein leerer
   * Vector geliefert. Die Reihenfolge der Spalten entspricht der Reihenfolge der Werte, wie sie von
   * {@link #getValuesForDataset(int)} geliefert werden.
   */
  public List<String> getColumnNames()
  {
    List<String> columnNames = null;
    
    try
    {
      switch (currentSourceType)
      {
      case CALC:
        columnNames = getColumnNames(selectedCalcDoc, tableName);
        break;
      case DB:
        Object table = getDbTableByName(selectedDBModel.datasourceName, selectedDBModel.getTableNames().get(0));
        columnNames = getDbColumns(table);
        break;
      default:
        columnNames = new ArrayList<>();
        break;
      }
    } catch (Exception x)
    {
      LOGGER.error("", x);
      return Collections.emptyList();
    }
        
    return columnNames;
  }
  
  public boolean checkUnmappedFields(List<String> columnNames) {
        return documentController.getModel().getReferencedFieldIDsThatAreNotInSchema(new HashSet<>(
        columnNames)).length > 0;
  }

  /**
   * Liefert die sichtbaren Inhalte (als Strings) der Zellen aus der rowIndex-ten sichtbaren
   * nicht-leeren Zeile (wobei die erste solche Zeile, diejenige die die Namen for
   * {@link #getColumnNames()} liefert, den Index 0 hat) aus der aktuell ausgewählten Tabelle. Falls
   * sich die Daten zwischen den Aufrufen der beiden Methoden nicht geändert haben, passen die
   * zurückgelieferten Daten in Anzahl und Reihenfolge genau zu der von {@link #getColumnNames()}
   * gelieferten Liste.
   *
   * Falls rowIndex zu groß ist, wird ein Vektor mit leeren Strings zurückgeliefert. Im Fehlerfall
   * wird ein leerer Vektor zurückgeliefert.
   */
  public List<String> getValuesForDataset(int rowIndex)
  {
    try
    {
      switch (currentSourceType)
      {
      case CALC:
        return getValuesForDataset(selectedCalcDoc, tableName, rowIndex);
      case DB:
        return getDbValuesForDataset(getOOoDatasource(), rowIndex);
      default:
        return new ArrayList<>();
      }
    } catch (Exception x)
    {
      LOGGER.error("", x);
      return new ArrayList<>();
    }
  }

  /**
   * Liefert die Anzahl der Datensätze der aktuell ausgewählten Tabelle. Ist derzeit keine Tabelle
   * ausgewählt oder enthält die ausgewählte Tabelle keine benannten Spalten, so wird 0 geliefert.
   */
  public int getNumberOfDatasets()
  {
    try
    {
      switch (currentSourceType)
      {
      case CALC:
        return getNumberOfDatasets(selectedCalcDoc, tableName);
      case DB:
        return getDbNumberOfDatasets();
      default:
        return 0;
      }
    } catch (Exception x)
    {
      LOGGER.error("", x);
      return 0;
    }
  }

  /**
   * Liefert true gdw die Funktion {@link #addColumns(Map)} verfügbar ist für diese Datenquelle.
   */
  public boolean supportsAddColumns()
  {
    switch (currentSourceType)
    {
    case CALC:
      return true;
    case DB:
      return false;
    default:
      return false;
    }
  }

  /**
   * Liefert den Inhalt der aktuell ausgewählten Serienbriefdatenquelle (leer, wenn keine
   * ausgewählt).
   */
  public QueryResultsWithSchema getData()
  {
    try
    {
      switch (currentSourceType)
      {
      case CALC:
        return getData(selectedCalcDoc, tableName);
      case DB:
        return getDbData(getOOoDatasource());
      default:
        return new QueryResultsWithSchema();
      }
    } catch (Exception x)
    {
      LOGGER.error("", x);
      return new QueryResultsWithSchema();
    }
  }

  public int getPreviewDatasetNumber()
  {
    return this.previewDatasetNumber;
  }

  public void updatePreviewFields(int previewDatasetNumber)
  {
    if (!hasDatasource())
    {
      return;
    }

    String previewDatasetNumberStr = "" + previewDatasetNumber;

    this.previewDatasetNumber = previewDatasetNumber;
    List<String> schema = getColumnNames();
    List<String> data = getValuesForDataset(previewDatasetNumber);

    if (schema.size() != data.size())
    {
      LOGGER.error(L.m("Daten haben sich zwischen dem Auslesen von Schema und Werten verändert"));
      return;
    }

    Iterator<String> dataIter = data.iterator();
    for (String column : schema)
    {
      WollMuxEventHandler.getInstance().handleSetFormValue(documentController.getModel().doc,
          column, dataIter.next(), null);
    }
    WollMuxEventHandler.getInstance().handleSetFormValue(documentController.getModel().doc,
        MailMergeNew.TAG_DATENSATZNUMMER, previewDatasetNumberStr, null);
    WollMuxEventHandler.getInstance().handleSetFormValue(documentController.getModel().doc,
        MailMergeNew.TAG_SERIENBRIEFNUMMER, previewDatasetNumberStr, null);
  }

  /**
   * Liefert true, wenn derzeit eine Datenquelle ausgewählt ist.
   */
  public boolean hasDatasource()
  {
    return currentSourceType != SOURCE_TYPE.NONE;
  }

  /**
   * Öffnet einen FilePicker und falls der Benutzer dort eine Tabelle auswählt, wird diese geöffnet
   * und als Datenquelle verwendet.
   */
  public CalcModel selectFileAsDatasource()
  {
    XFilePicker picker = UNO
        .XFilePicker(UNO.createUNOService("com.sun.star.ui.dialogs.FilePicker"));
    picker.setMultiSelectionMode(false);

    short res = picker.execute();
    String[] sheets = null;
    String windowTitle = "";

    CalcModel model = null;
    if (res == com.sun.star.ui.dialogs.ExecutableDialogResults.OK)
    {
      String[] files = picker.getFiles();
      try
      {
        LOGGER.debug(L.m("Öffne %1 als Datenquelle für Seriendruck", files[0]));
        XSpreadsheetDocument doc = getCalcDocByFile(files[0]);

        if (doc == null)
          return null;

        sheets = doc.getSheets().getElementNames();
        String docUrl = UNO.XModel(doc).getURL();

        windowTitle = UNO.getPropertyByPropertyValues(UNO.XModel(doc).getArgs(), "Title");

        model = new CalcModel(stripOpenOfficeFromWindowName(windowTitle), docUrl, sheets, doc);
        openSpreedSheetDocuments.add(model);

        if (sheets.length > 0)
        {
          setDatasource(sheets[0]);
        }
      } catch (Exception e)
      {
        LOGGER.error("", e);
      }
    }

    return model;
  }

  /**
   * Öffnet die Datenquelle die durch einen früheren Aufruf von storeDatasourceSettings() im
   * Dokument hinterlegt wurde.
   */
  private void openDatasourceFromLastStoredSettings()
  {
    ConfigThingy mmconf = documentController.getModel().getMailmergeConfig();
    ConfigThingy datenquelle = new ConfigThingy("");
    try
    {
      datenquelle = mmconf.query("Datenquelle").getLastChild();
    } catch (NodeNotFoundException e)
    {
    }

    String type = datenquelle.getString("TYPE", null);

    if ("calc".equalsIgnoreCase(type))
    {
      try
      {
        String url = datenquelle.get("URL").toString();
        String table = datenquelle.get("TABLE").toString();
        try
        {
          Object d = getCalcDocByUrl(url);
          if (d != null)
          {
            setDatasource(table);
          }
        } catch (UnavailableException e)
        {
          LOGGER.debug("", e);
        }
      } catch (NodeNotFoundException e)
      {
        LOGGER.error(L.m("Fehlendes Argument für Datenquelle vom Typ '%1':", type), e);
      }
    } else if ("ooo".equalsIgnoreCase(type))
    {
      try
      {
        String source = datenquelle.get("SOURCE").toString();
        String table = datenquelle.get("TABLE").toString();
        getOOoDatasource(source);
        setDatasource(table);
      } catch (NodeNotFoundException e)
      {
        LOGGER.error(L.m("Fehlendes Argument für Datenquelle vom Typ '%1':", type), e);
      }
    } else if (type != null)
    {
      LOGGER.error(L.m("Ignoriere Datenquelle mit unbekanntem Typ '%1'", type));
    }
  }

  /**
   * Speichert die aktuellen Einstellungen zu dieser Datenquelle im zugehörigen Dokument persistent
   * ab, damit die Datenquelle beim nächsten mal wieder automatisch geöffnet/verbunden werden kann.
   */
  private void storeDatasourceSettings()
  {
    // ConfigThingy für Einstellungen der Datenquelle erstellen:
    ConfigThingy dq = new ConfigThingy("Datenquelle");
    ConfigThingy arg;
    switch (currentSourceType)
    {
    case CALC:
      if (calcUrl == null || tableName.length() == 0)
      {
        break;
      }
      arg = new ConfigThingy("TYPE");
      arg.addChild(new ConfigThingy("calc"));
      dq.addChild(arg);
      arg = new ConfigThingy("URL");
      arg.addChild(new ConfigThingy(calcUrl));
      dq.addChild(arg);
      arg = new ConfigThingy("TABLE");
      arg.addChild(new ConfigThingy(tableName));
      dq.addChild(arg);
      break;
    case DB:
      if (selectedDBModel == null || selectedDBModel.datasourceName == null || selectedDBModel.getTableNames().size() == 0)
      {
        break;
      }
      arg = new ConfigThingy("TYPE");
      arg.addChild(new ConfigThingy("ooo"));
      dq.addChild(arg);
      arg = new ConfigThingy("SOURCE");
      arg.addChild(new ConfigThingy(selectedDBModel.datasourceName));
      dq.addChild(arg);
      arg = new ConfigThingy("TABLE");
      arg.addChild(new ConfigThingy(tableName));
      dq.addChild(arg);
      break;

    case NONE:
      break;
    }

    ConfigThingy seriendruck = new ConfigThingy("Seriendruck");
    if (dq.count() > 0)
    {
      seriendruck.addChild(dq);
    }

    documentController.setMailmergeConfig(seriendruck);
  }

  /**
   * Liefert die Anzahl Datensätze aus OOo-Datenquelle oooDatasourceName, Tabelle tableName.
   */
  public int getDbNumberOfDatasets()
  {
    if (currentSourceType != SOURCE_TYPE.DB)
    {
      return 0;
    }

    XRowSet results = null;
    XConnection conn = null;
    try
    {
      try
      {
        XDataSource ds = UNO.XDataSource(UNO.dbContext.getRegisteredObject(selectedDBModel.datasourceName));
        long lgto = MAILMERGE_LOGIN_TIMEOUT / 1000;
        if (lgto < 1)
        {
          lgto = 1;
        }
        ds.setLoginTimeout((int) lgto);
        conn = ds.getConnection("", "");
      } catch (Exception x)
      {
        throw new TimeoutException(
            L.m("Kann keine Verbindung zur Datenquelle \"%1\" herstellen", selectedDBModel.datasourceName));
      }

      Object rowSet = UNO.createUNOService("com.sun.star.sdb.RowSet");
      results = UNO.XRowSet(rowSet);

      XPropertySet xProp = UNO.XPropertySet(results);

      xProp.setPropertyValue("ActiveConnection", conn);

      /*
       * EscapeProcessing == false bedeutet, dass OOo die Query nicht selbst anfassen darf, sondern
       * direkt an die Datenbank weiterleiten soll. Wird dies verwendet ist das Ergebnis (derzeit)
       * immer read-only, da OOo keine Updates von Statements durchführen kann, die es nicht geparst
       * hat. Siehe Kommentar zu http://qa.openoffice.org/issues/show_bug.cgi?id=78522 Entspricht
       * dem Button SQL mit grünem Haken (SQL-Kommando direkt ausführen) im Base-Abfrageentwurf.
       */
      xProp.setPropertyValue("EscapeProcessing", Boolean.FALSE);

      xProp.setPropertyValue("CommandType", Integer.valueOf(com.sun.star.sdb.CommandType.COMMAND));

      xProp.setPropertyValue("Command", "SELECT COUNT(*) FROM " + sqlIdentifier(tableName) + ";");

      results.execute();

      results.first();
      XRow row = UNO.XRow(results);

      return row.getInt(1);
    } catch (Exception x)
    {
      LOGGER.error("", x);
      return 0;
    } finally
    {
      if (results != null)
      {
        UNO.XComponent(results).dispose();
      }
      if (conn != null)
      {
        try
        {
          conn.close();
        } catch (Exception e)
        {
        }
      }
    }
  }

  /**
   * Liefert str zurück, als Identifier-Name vorbereitet für das Einfügen in SQL-Statements.
   *
   * @param str
   *          beginnt und endet immer mit einem Doublequote.
   */
  private static String sqlIdentifier(String str)
  {
    return "\"" + str.replaceAll("\"", "\"\"") + "\"";
  }

  /**
   * Liefert die Anzahl Zeilen in Tabelle tableName von Calc-Dokument calcDoc in denen mindestens
   * eine sichtbare nicht-leere Zelle ist, wobei die erste sichtbare Zeile nicht gezählt wird, weil
   * diese die Spaltennamen beschreibt.
   */
  private int getNumberOfDatasets(XSpreadsheetDocument calcDoc, String tableName)
  {
    if (calcDoc != null)
    {
      try
      {
        XCellRangesQuery sheet = UNO.XCellRangesQuery(calcDoc.getSheets().getByName(tableName));
        SortedSet<Integer> columnIndexes = new TreeSet<>();
        SortedSet<Integer> rowIndexes = new TreeSet<>();
        getVisibleNonemptyRowsAndColumns(sheet, columnIndexes, rowIndexes);

        if (!columnIndexes.isEmpty() && rowIndexes.size() > 1)
        {
          return rowIndexes.size() - 1;
        }
      } catch (Exception x)
      {
        LOGGER.error(L.m("Kann Anzahl Datensätze nicht bestimmen"), x);
      }
    }

    return 0;
  }

  /**
   * Liefert die Spaltennamen der Tabelle tableName aus der OOo-Datenquelle oooDatasourceName in
   * alphabetischer Reihenfolge. Die Reihenfolge entspricht der von
   * {@link #getDbValuesForDataset(Datasource, int)}.
   */
  private List<String> getDbColumnNames(Datasource oooDatasource)
  {
    List<String> columnNames = new ArrayList<>();
    columnNames.addAll(oooDatasource.getSchema());
    Collections.sort(columnNames);
    return columnNames;
  }

  /**
   * Liefert die Inhalte (als Strings) der nicht-leeren Zellen der ersten sichtbaren Zeile von
   * Tabellenblatt tableName in Calc-Dokument calcDoc.
   */
  private List<String> getColumnNames(XSpreadsheetDocument calcDoc, String tableName)
  {
    List<String> columnNames = new ArrayList<>();
    if (calcDoc == null)
    {
      return columnNames;
    }
    try
    {
      XCellRangesQuery sheet = UNO.XCellRangesQuery(calcDoc.getSheets().getByName(tableName));
      SortedSet<Integer> columnIndexes = new TreeSet<>();
      SortedSet<Integer> rowIndexes = new TreeSet<>();
      getVisibleNonemptyRowsAndColumns(sheet, columnIndexes, rowIndexes);

      if (!columnIndexes.isEmpty() && !rowIndexes.isEmpty())
      {
        XCellRange sheetCellRange = UNO.XCellRange(sheet);

        /*
         * Erste sichtbare Zeile durchscannen und alle nicht-leeren Zelleninhalte als
         * Tabellenspaltennamen interpretieren.
         */
        int ymin = rowIndexes.first().intValue();
        Iterator<Integer> iter = columnIndexes.iterator();
        while (iter.hasNext())
        {
          int x = iter.next().intValue();
          String columnName = UNO.XTextRange(sheetCellRange.getCellByPosition(x, ymin)).getString();
          if (columnName.length() > 0)
          {
            columnNames.add(columnName);
          }
        }
      }
    } catch (Exception x)
    {
      LOGGER.error(L.m("Kann Spaltennamen nicht bestimmen"), x);
    }
    return columnNames;
  }

  /**
   * Liefert die Daten des rowIndex-ten Datensatzes der Datenquelle oooDatasource (wobei der erste
   * Datensatz die Nummer 1 hat!!!) Falls sich die Daten zwischen den Aufrufen der beiden Methoden
   * nicht geändert haben, passen die zurückgelieferten Daten in Anzahl und Reihenfolge genau zu der
   * von {@link #getDbColumnNames(Datasource)} gelieferten Liste.
   *
   * Falls rowIndex zu groß ist, wird ein Vektor mit leeren Strings zurückgeliefert. Im Fehlerfall
   * wird ein leerer Vektor zurückgeliefert.
   */
  private List<String> getDbValuesForDataset(Datasource oooDatasource, int rowIndex)
  {
    /*
     * Die folgende Implementierung ist nicht schön. Sie hat die folgenden Probleme
     *
     * o Liest jedes Mal erneut die ganze Tabelle aus => langsam, übermäßige Garbage Produktion
     *
     * o Kann kein Ergebnis zurückliefern, wenn das Auslesen der gesamten Tabelle nicht innerhalb
     * des Timeouts möglich ist => Vorschau broken bei langsamen Datenquellen
     *
     * Der große Vorteil dieser Implementierung ist ihre Einfachheit.
     */

    List<String> list = getDbColumnNames(oooDatasource);
    try
    {
      if (rowIndex < 1)
        throw new IllegalArgumentException(L.m("Illegale Datensatznummer: %1", rowIndex));
      QueryResults res = oooDatasource.getContents(MAILMERGE_GETCONTENTS_TIMEOUT);
      for (Dataset ds : res)
      {
        if (--rowIndex == 0)
        {
          // Avoid needless obj creation by overwriting col names with return values
          for (int i = 0; i < list.size(); ++i)
          {
            String str;
            try
            {
              str = ds.get(list.get(i));
            } catch (ColumnNotFoundException x)
            {
              str = "";
            }
            list.set(i, str);
          }
          return list;
        }
      }

      // Avoid needless object creation by overwriting col names with return values
      for (int i = 0; i < list.size(); ++i)
        list.set(i, "");
      return list;
    } catch (Exception x)
    {
      LOGGER.error("", x);
      return new ArrayList<>();
    }
  }

  /**
   * Liefert die sichtbaren Inhalte (als Strings) der Zellen aus der rowIndex-ten sichtbaren
   * nicht-leeren Zeile (wobei die erste solche Zeile, diejenige die die Namen for
   * {@link #getColumnNames()} liefert, den Index 0 hat) von Tabellenblatt tableName in
   * Calc-Dokument calcDoc. Falls sich die Daten zwischen den Aufrufen der beiden Methoden nicht
   * geändert haben, passen die zurückgelieferten Daten in Anzahl und Reihenfolge genau zu der von
   * {@link #getColumnNames()} gelieferten Liste.
   *
   * Falls rowIndex zu groß ist, wird ein Vektor mit leeren Strings zurückgeliefert. Im Fehlerfall
   * wird ein leerer Vektor zurückgeliefert.
   *
   */
  private List<String> getValuesForDataset(XSpreadsheetDocument calcDoc, String tableName,
      int rowIndex)
  {
    List<String> columnValues = new ArrayList<>();
    if (calcDoc == null)
    {
      return columnValues;
    }
    try
    {
      XCellRangesQuery sheet = UNO.XCellRangesQuery(calcDoc.getSheets().getByName(tableName));
      SortedSet<Integer> columnIndexes = new TreeSet<>();
      SortedSet<Integer> rowIndexes = new TreeSet<>();
      getVisibleNonemptyRowsAndColumns(sheet, columnIndexes, rowIndexes);

      if (!columnIndexes.isEmpty())
      {
        XCellRange sheetCellRange = UNO.XCellRange(sheet);

        /*
         * Den Zeilenindex in der Tabelle von der rowIndex-ten sichtbaren Zeile bestimmen.
         */
        int yTargetRow = -1;
        int count = rowIndex;

        for (int index : rowIndexes)
        {
          if (--count < 0)
          {
            yTargetRow = index;
            break;
          }
        }

        /*
         * Erste sichtbare Zeile durchscannen und alle nicht-leeren Zelleninhalte als
         * Tabellenspaltennamen interpretieren (dies ist nötig, damit die zurückgelieferten Werte zu
         * denen von getColumnNames() passen). Wurde ein Tabellenspaltenname identifiziert, so lies
         * den zugehörigen Wert aus der rowIndex-ten Zeile aus.
         */
        int ymin = rowIndexes.first().intValue();
        Iterator<Integer> iter = columnIndexes.iterator();
        while (iter.hasNext())
        {
          int x = iter.next().intValue();
          String columnName = UNO.XTextRange(sheetCellRange.getCellByPosition(x, ymin)).getString();
          if (columnName.length() > 0)
          {
            if (yTargetRow >= 0)
            {
              String columnValue = UNO.XTextRange(sheetCellRange.getCellByPosition(x, yTargetRow))
                  .getString();
              columnValues.add(columnValue);
            } else
              columnValues.add("");
          }
        }
      }
    } catch (Exception x)
    {
      LOGGER.error(L.m("Kann Spaltenwerte nicht bestimmen"), x);
    }
    return columnValues;
  }

  /**
   * Liefert den Inhalt der Tabelle tableName aus der OOo Datenquelle mit Namen oooDatasourceName.
   *
   */
  private QueryResultsWithSchema getDbData(Datasource oooDatasource) throws Exception
  {
    Set<String> schema = oooDatasource.getSchema();
    QueryResults res = oooDatasource.getContents(MAILMERGE_GETCONTENTS_TIMEOUT);
    return new QueryResultsWithSchema(res, schema);
  }

  /**
   * Liefert die sichtbaren Zellen aus der Tabelle tableName des Dokuments calcDoc als
   * QueryResultsWithSchema zurück.
   */
  private QueryResultsWithSchema getData(XSpreadsheetDocument calcDoc, String tableName)
  {
    Set<String> schema = new HashSet<>();
    QueryResults res = getVisibleCalcData(calcDoc, tableName, schema);
    return new QueryResultsWithSchema(res, schema);
  }

  /**
   * Öffnet ein neues Calc-Dokument und setzt es als Seriendruckdatenquelle.
   */
  public CalcModel openAndselectNewCalcTableAsDatasource()
  {
    CalcModel calcModel = null;

    try
    {
      LOGGER.debug(L.m("Öffne neues Calc-Dokument als Datenquelle für Seriendruck"));
      XSpreadsheetDocument spread = UNO
          .XSpreadsheetDocument(UNO.loadComponentFromURL("private:factory/scalc", true, true));
      XSpreadsheets sheets = spread.getSheets();
      String[] sheetNames = sheets.getElementNames();

      String title = UNO.getPropertyByPropertyValues(UNO.XModel(spread).getArgs(), "Title");
      calcModel = new CalcModel(stripOpenOfficeFromWindowName(title), UNO.XModel(spread).getURL(),
          spread.getSheets().getElementNames(), spread);

      openSpreedSheetDocuments.add(calcModel);
      this.currentSourceType = SOURCE_TYPE.CALC;
      setDatasource(sheetNames[0]);

    } catch (Exception e)
    {
      LOGGER.error("", e);
    }

    return calcModel;
  }
  
  private List<DBModel> cachedDBConnections = new ArrayList<>();
  
  public void addCachedDbConnection(DBModel dbModel) {
    cachedDBConnections.add(dbModel);
  }
  
  private DBModel selectedDBModel = null;

  /**
   * Setzt die zu verwendende Tabelle auf den Namen name und speichert die Einstellungen persistent
   * im zugehörigen Dokument ab, damit sie bei der nächsten Bearbeitung des Dokuments wieder
   * verfügbar sind.
   *
   * @param name
   *          Name der Tabelle die aktuell eingestellt werden soll.
   */
  public void setDatasource(String name)
  {
    if (name == null)
    {
      LOGGER.error("MailMergeDatasource: setDatasource: name is NULL.");
      return;
    }
    
    if (openSpreedSheetDocuments.isEmpty())
    {
      currentSourceType = SOURCE_TYPE.DB;
      
      for (DBModel model : cachedDBConnections) {
        for (String tableName : model.getTableNames()) {
          if (tableName.equals(name)) {
            selectedDBModel = model;
            this.tableName = tableName;
            break;
          }
        }
      }
    }

    openSpreedSheetDocuments.stream().forEach(item -> {
      for (String tableName : item.getSpreadSheetTableTitles())
      {
        if (name.contains(tableName) && name.contains(item.getWindowTitle()))
        {
          calcUrl = item.getCalcUrl();
          selectedCalcDoc = item.getSpreadSheetDocument();
          this.tableName = tableName;
          currentSourceType = SOURCE_TYPE.CALC;
          oooDatasource = null;
          break;
        } else 
        {
          currentSourceType = SOURCE_TYPE.DB;
          for (DBModel model : cachedDBConnections) {
            for (String dbTableName : model.getTableNames()) {
              if (dbTableName.equals(name)) {
                selectedDBModel = model;
                break;
              }
            }
          }
        }
      }
    });

    storeDatasourceSettings();
  }

  // FIXME: Refaktorisieren an sinnvolle Stelle, am besten in UNOHelper
  public static String stripOpenOfficeFromWindowName(String str)
  {
    /*
     * Sonderfall für OpenOffice
     */
    int idx = str.indexOf(" - OpenOffice");
    /* Fallback für andere Office-Varianten */
    if (idx < 0)
    {
      idx = str.lastIndexOf(" -");
    }
    if (idx > 0)
    {
      str = str.substring(0, idx);
    }
    return str;
  }

  public List<CalcModel> openSpreedSheetDocuments = new ArrayList<>();

  /**
   * Liefert ein CalcModel und zugehörigen XSpreadsheetDocuments aller offenen Calc-Fenster.
   *
   * @return Map<CalcModel, XSpreadsheetDocument> Das CalcModel entält den Titel des Calc-Fensters,
   *         die Datei-URL sowie eine Liste mit Tabellennamen des Dokuments. Das zweite Objekt der
   *         MAp ist das eigentliche UNO Dokumente, über XSpreedSheetDocument.getSheets können die
   *         Tabellen abgerufen werden.
   */
  public List<CalcModel> getOpenCalcWindows()
  {
    try
    {
      XSpreadsheetDocument spread = null;
      XEnumeration xenu = UNO.desktop.getComponents().createEnumeration();

      while (xenu.hasMoreElements())
      {
        spread = UNO.XSpreadsheetDocument(xenu.nextElement());
        if (spread != null)
        {
          String title = UNO.getPropertyByPropertyValues(UNO.XModel(spread).getArgs(), "Title");
          openSpreedSheetDocuments.add(new CalcModel(stripOpenOfficeFromWindowName(title),
              UNO.XModel(spread).getURL(), spread.getSheets().getElementNames(), spread));
        }
      }
    } catch (Exception x)
    {
      LOGGER.error("", x);
    }

    return openSpreedSheetDocuments;
  }

  private XSpreadsheetDocument getCalcDocByFile(String url)
  {
    XSpreadsheetDocument doc = null;

    Object ss = null;
    try
    {
      ss = UNO.loadComponentFromURL(url, false, true);
    } catch (UnoHelperException e)
    {
      LOGGER.error("", e);
    }
    doc = UNO.XSpreadsheetDocument(ss);

    return doc;
  }

  private XSpreadsheetDocument getCalcDocByUrl(String url) throws UnavailableException
  {
    XSpreadsheetDocument result = null;

    try
    {
      XSpreadsheetDocument spread;
      XEnumeration xenu = UNO.desktop.getComponents().createEnumeration();
      while (xenu.hasMoreElements())
      {
        spread = UNO.XSpreadsheetDocument(xenu.nextElement());
        if (spread != null && url.equals(UNO.XModel(spread).getURL()))
        {
          result = spread;
          break;
        }
      }
    } catch (Exception x)
    {
      LOGGER.error("", x);
    }

    return result;
  }

  /**
   * Falls aktuell eine OOo-Datenquelle als Datenquelle ausgewählt ist, so wird diese
   * zurückgeliefert. Falls es aus irgendeinem Grund nicht möglich ist, diese zurückzuliefern, wird
   * eine {@link de.muenchen.allg.itd51.wollmux.core.exceptions.UnavailableException} geworfen.
   *
   * @author Matthias Benkmann (D-III-ITD-D101)
   *
   */
  public Datasource getOOoDatasource() throws UnavailableException
  {
    if (currentSourceType != SOURCE_TYPE.DB)
      throw new UnavailableException(L.m("Keine OOo-Datenquelle ausgewählt"));
    if (oooDatasource != null)
    {
      return oooDatasource;
    }

    ConfigThingy conf = new ConfigThingy("Datenquelle");
    conf.add("NAME").add("Knuddel");
    conf.add("TABLE").add(tableName);
    conf.add("SOURCE").add(selectedDBModel.datasourceName);
    try
    {
      oooDatasource = new OOoDatasource(new HashMap<String, Datasource>(), conf, true);
    } catch (Exception x)
    {
      throw new UnavailableException(x);
    }

    return oooDatasource;
  }

  /**
   * Setzt die registrierte Datenquelle mit Namen newDsName als neue Datenquelle für den
   * Seriendruck.
   *
   * @author Matthias Benkmann (D-III-ITD-D101)
   *
   *         TESTED
   */
  private void getOOoDatasource(String newDsName)
  {
    try
    {
      UNO.XNameAccess(UNO.dbContext).getByName(newDsName);
    } catch (DisposedException x)
    {
      return;
    } catch (Exception x)
    {
      LOGGER.error("", x);
      return;
    }
    if (newDsName.isEmpty())
    {
      return;
    }

    currentSourceType = SOURCE_TYPE.DB;
    selectedCalcDoc = null;

    selectedDBModel.datasourceName = newDsName;
    oooDatasource = null;
    storeDatasourceSettings();
  }

  /**
   * Liefert den Spaltennamen (z,B, "A") zu Spalte col. Dabei ist Spalte 1 = "A".
   *
   * @author Matthias Benkmann (D-III-ITD-D101)
   *
   *         TESTED
   */
  private static String getCalcColumnNameForColumnIndex(int col)
  {
    StringBuilder buffy = new StringBuilder();
    do
    {
      --col;
      buffy.insert(0, (char) ('A' + (col % 26)));
    } while ((col = col / 26) > 0);
    return buffy.toString();
  }

  /**
   * Falls unterstützt (siehe {@link #supportsAddColumns()}), wird für jedes Mapping fieldId->subst
   * eine neue Spalte in die Datenbank eingefügt mit Titel fieldId, deren Inhalt durch subst
   * beschrieben wird. Referenzen auf andere Felder innerhalb von subst werden interpretiert als
   * Referenzen auf die Tabellenspalten mit entsprechenden Titeln.
   *
   * @author Matthias Benkmann (D-III-ITD-D101)
   *
   *         TESTED
   */
  public void addColumns(Map<String, FieldSubstitution> mapIdToSubstitution)
  {
    if (currentSourceType != SOURCE_TYPE.CALC)
    {
      return;
    }

    XCellRangesQuery sheet;
    try
    {
      sheet = UNO.XCellRangesQuery(selectedCalcDoc.getSheets().getByName(tableName));
    } catch (Exception x)
    {
      return;
    }

    /*
     * Indizes sichtbarer Zellen sowie Mapping von Spaltennamen (basierend auf erster sichtbarer
     * Zeile) auf Calc-Spaltennamen (z.B. "A") erstellen.
     */
    Map<String, String> mapColumnNameToCalcColumnName = new HashMap<>();
    SortedSet<Integer> columnIndexes = new TreeSet<>();
    SortedSet<Integer> rowIndexes = new TreeSet<>();
    try
    {
      getVisibleNonemptyRowsAndColumns(sheet, columnIndexes, rowIndexes);

      if (!columnIndexes.isEmpty() && !rowIndexes.isEmpty())
      {
        XCellRange sheetCellRange = UNO.XCellRange(sheet);

        /*
         * Erste sichtbare Zeile durchscannen und alle nicht-leeren Zelleninhalte als
         * Tabellenspaltennamen interpretieren.
         */
        int ymin = rowIndexes.first().intValue();
        Iterator<Integer> iter = columnIndexes.iterator();
        while (iter.hasNext())
        {
          int x = iter.next().intValue();
          String columnName = UNO.XTextRange(sheetCellRange.getCellByPosition(x, ymin)).getString();
          if (columnName.length() > 0)
          {
            mapColumnNameToCalcColumnName.put(columnName, getCalcColumnNameForColumnIndex(x + 1));
          }
        }
      }
    } catch (Exception x)
    {
      LOGGER.error(L.m("Fehler beim Zugriff auf Calc-Dokument"), x);
      return;
    }

    // Erste neue Spalte hinter die letzte Spalte
    int newColumnX = columnIndexes.last() + 1;

    for (Map.Entry<String, FieldSubstitution> ent : mapIdToSubstitution.entrySet())
    {
      String fieldId = ent.getKey();
      FieldSubstitution subst = ent.getValue();
      StringBuilder formula = new StringBuilder();
      for (FieldSubstitution.SubstElement substEle : subst)
      {
        if (formula.length() == 0)
          formula.append("=CONCATENATE(");
        else
          formula.append(';');
        if (substEle.isFixedText())
        {
          formula.append('"');
          formula.append(substEle.getValue().replaceAll("\"", "\"\""));
          formula.append('"');
        } else if (substEle.isField())
        {
          String calcColumnName = mapColumnNameToCalcColumnName.get(substEle.getValue());
          if (calcColumnName != null)
          {
            formula.append(calcColumnName);
            formula.append(ROW_NUM_PLACEHOLDER);
          } else
          {
            formula.append("\"<");
            formula.append(substEle.getValue().replaceAll("\"", "\"\""));
            formula.append(ROW_NUM_PLACEHOLDER);
            formula.append(">\"");
          }
        } else
          formula.append("\"UNKNOWN\"");
      }

      // Wenn Formel leer ist, brauchen wir nichts zu tun
      if (formula.length() == 0)
      {
        continue;
      }

      formula.append(')');
      String formulaStr = formula.toString();

      try
      {
        XCellRange sheetCellRange = UNO.XCellRange(sheet);

        int ymin = rowIndexes.first();
        int ymax = rowIndexes.last();
        UNO.XTextRange(sheetCellRange.getCellByPosition(newColumnX, ymin)).setString(fieldId);

        /*
         * KEINE zusätzlichen Zeilen mit der Formel belegen, weil ansonsten bei Eingabe eines fixen
         * Textes, auch wenn es nur ein Leerzeichen zwischen <Vorname> und <Nachname> ist, dazu
         * führt, dass 1007 zusätzliche Datensätze erkannt werden.
         */
        for (int y = ymin + 1; y <= ymax + 0; ++y)
        {
          UNO.XCell(sheetCellRange.getCellByPosition(newColumnX, y))
              .setFormula(formulaStr.replaceAll(ROW_NUM_PLACEHOLDER, "" + (y + 1)));
        }

        ++newColumnX;

      } catch (Exception x)
      {
        LOGGER.error(L.m("Kann Spalte \"%1\" nicht hinzufügen", fieldId), x);
      }
    }
  }

  /**
   * Liefert die Namen aller Tabellen der aktuell ausgewählten OOo-Datenquelle. Wenn keine
   * OOo-Datenquelle ausgewählt ist, oder es keine nicht-leere Tabelle gibt, so wird eine leere
   * Liste geliefert.
   *
   * @author Matthias Benkmann (D-III-ITD 5.1)
   *
   *         TESTED
   */
  public List<String> getDbTableNames(String oooDatasourceName)
  {
    List<String> tableNames = new ArrayList<>();

    try
    {
      XDataSource ds = UNO.XDataSource(UNO.dbContext.getRegisteredObject(oooDatasourceName));
      long lgto = MAILMERGE_LOGIN_TIMEOUT / 1000;
      if (lgto < 1)
      {
        lgto = 1;
      }
      ds.setLoginTimeout((int) lgto);
      XConnection conn = ds.getConnection("", "");
      XNameAccess tables = UNO.XTablesSupplier(conn).getTables();
      for (String name : tables.getElementNames())
        tableNames.add(name);
      XNameAccess queries = UNO.XQueriesSupplier(conn).getQueries();
      for (String name : queries.getElementNames())
        tableNames.add(name);
    } catch (Exception x)
    {
      LOGGER.error("", x);
    }

    return tableNames;
  }
  
  public Object getDbTableByName(String datasourceName, String tableName) 
  {    
    try
    {
      XDataSource ds = UNO.XDataSource(UNO.dbContext.getRegisteredObject(datasourceName));
      long lgto = MAILMERGE_LOGIN_TIMEOUT / 1000;
      if (lgto < 1)
      {
        lgto = 1;
      }
      ds.setLoginTimeout((int) lgto);
      XConnection conn = ds.getConnection("", "");
      XNameAccess tables = UNO.XTablesSupplier(conn).getTables();
      
      return tables.getByName(tableName);
    } catch (Exception x)
    {
      LOGGER.error("", x);
    }

    return null;
  }
  
  public List<String> getDbColumns(Object table) {
    XColumnsSupplier columnsSupplier = UnoRuntime.queryInterface(XColumnsSupplier.class, table);
    
    if (columnsSupplier == null)
      return Collections.emptyList();
    
    List<String> columnNames = new ArrayList<>();
    
    XNameAccess xColumns = columnsSupplier.getColumns();
    String[] aColumnNames = xColumns.getElementNames();
    for ( int i = 0; i <= aColumnNames.length - 1; i++ )
    {
        columnNames.add(aColumnNames[i]);
    }
    
    return columnNames;
  }

  /**
   * Liefert die sichtbaren Zellen des Arbeitsblattes mit Namen sheetName aus dem Calc Dokument doc.
   * Die erste sichtbare Zeile der Calc-Tabelle wird herangezogen als Spaltennamen. Diese
   * Spaltennamen werden zu schema hinzugefügt.
   *
   * @author Matthias Benkmann (D-III-ITD 5.1) TESTED
   */
  private static QueryResults getVisibleCalcData(XSpreadsheetDocument doc, String sheetName,
      Set<String> schema)
  {
    MailMergeDatasource.CalcCellQueryResults results = new CalcCellQueryResults();

    try
    {
      if (doc != null)
      {
        XCellRangesQuery sheet = UNO.XCellRangesQuery(doc.getSheets().getByName(sheetName));
        if (sheet != null)
        {
          SortedSet<Integer> columnIndexes = new TreeSet<>();
          SortedSet<Integer> rowIndexes = new TreeSet<>();
          getVisibleNonemptyRowsAndColumns(sheet, columnIndexes, rowIndexes);

          if (!columnIndexes.isEmpty() && !rowIndexes.isEmpty())
          {
            XCellRange sheetCellRange = UNO.XCellRange(sheet);

            /*
             * Erste sichtbare Zeile durchscannen und alle nicht-leeren Zelleninhalte als
             * Tabellenspaltennamen interpretieren. Ein Mapping in mapColumnNameToIndex wird
             * erzeugt, wobei NICHT auf den Index in der Calc-Tabelle gemappt wird, sondern auf den
             * Index im später für jeden Datensatz existierenden String[]-Array.
             */
            int ymin = rowIndexes.first().intValue();
            Map<String, Integer> mapColumnNameToIndex = new HashMap<>();
            int idx = 0;
            Iterator<Integer> iter = columnIndexes.iterator();
            while (iter.hasNext())
            {
              int x = iter.next().intValue();
              String columnName = UNO.XTextRange(sheetCellRange.getCellByPosition(x, ymin))
                  .getString();
              if (columnName.length() > 0)
              {
                mapColumnNameToIndex.put(columnName, Integer.valueOf(idx));
                schema.add(columnName);
                ++idx;
              } else
                iter.remove(); // Spalten mit leerem Spaltennamen werden nicht
              // benötigt.
            }

            results.setColumnNameToIndexMap(mapColumnNameToIndex);

            /*
             * Datensätze erzeugen
             */
            Iterator<Integer> rowIndexIter = rowIndexes.iterator();
            rowIndexIter.next(); // erste Zeile enthält die Tabellennamen, keinen
            // Datensatz
            while (rowIndexIter.hasNext())
            {
              int y = rowIndexIter.next().intValue();
              String[] data = new String[columnIndexes.size()];
              Iterator<Integer> columnIndexIter = columnIndexes.iterator();
              idx = 0;
              while (columnIndexIter.hasNext())
              {
                int x = columnIndexIter.next().intValue();
                String value = UNO.XTextRange(sheetCellRange.getCellByPosition(x, y)).getString();
                data[idx++] = value;
              }

              results.addDataset(data);
            }
          }
        }
      }
    } catch (Exception x)
    {
      LOGGER.error("", x);
    }

    return results;
  }

  /**
   * Liefert von Tabellenblatt sheet die Indizes aller Zeilen und Spalten, in denen mindestens eine
   * sichtbare nicht-leere Zelle existiert.
   *
   * @param sheet
   *          das zu scannende Tabellenblatt
   * @param columnIndexes
   *          diesem Set werden die Spaltenindizes hinzugefügt
   * @param rowIndexes
   *          diesem Set werden die Zeilenindizes hinzugefügt
   * @author Matthias Benkmann (D-III-ITD 5.1)
   */
  private static void getVisibleNonemptyRowsAndColumns(XCellRangesQuery sheet,
      SortedSet<Integer> columnIndexes, SortedSet<Integer> rowIndexes)
  {
    XSheetCellRanges visibleCellRanges = sheet.queryVisibleCells();
    XSheetCellRanges nonEmptyCellRanges = sheet.queryContentCells(
        (short) (com.sun.star.sheet.CellFlags.VALUE | com.sun.star.sheet.CellFlags.DATETIME
            | com.sun.star.sheet.CellFlags.STRING | com.sun.star.sheet.CellFlags.FORMULA));
    CellRangeAddress[] nonEmptyCellRangeAddresses = nonEmptyCellRanges.getRangeAddresses();
    for (int i = 0; i < nonEmptyCellRangeAddresses.length; ++i)
    {
      XSheetCellRanges ranges = UNO.XCellRangesQuery(visibleCellRanges)
          .queryIntersection(nonEmptyCellRangeAddresses[i]);
      CellRangeAddress[] rangeAddresses = ranges.getRangeAddresses();
      for (int k = 0; k < rangeAddresses.length; ++k)
      {
        CellRangeAddress addr = rangeAddresses[k];
        for (int x = addr.StartColumn; x <= addr.EndColumn; ++x)
          columnIndexes.add(Integer.valueOf(x));

        for (int y = addr.StartRow; y <= addr.EndRow; ++y)
          rowIndexes.add(Integer.valueOf(y));
      }
    }
  }

  /**
   * Öffnet den Dialog zum Einfügen eines Spezialfeldes, das über die Funktion trafoConf beschrieben
   * ist, erzeugt daraus ein transformiertes Feld und fügt dieses Feld in das Dokument mod ein; Es
   * erwartet darüber hinaus den Namen des Buttons buttonName, aus dem das Label des Dialogs, und
   * später der Mouse-Over hint erzeugt wird und die Liste der aktuellen Felder, die evtl. im Dialog
   * zur Verfügung stehen sollen.
   *
   * @param fieldNames
   *          Eine Liste der Feldnamen, die der Dialog anzeigt, falls er Buttons zum Einfügen von
   *          Serienbrieffeldern bereitstellt.
   * @param buttonName
   *          Der Name des Buttons, aus dem die Titelzeile des Dialogs und der Mouse-Over Hint des
   *          neu erzeugten Formularfeldes generiert wird.
   * @param trafoConf
   *          ConfigThingy, das die Funktion und damit den aufzurufenden Dialog spezifiziert. Der
   *          von den Dialogen benötigte äußere Knoten "Func(...trafoConf...) wird dabei von dieser
   *          Methode erzeugt, so dass trafoConf nur die eigentliche Funktion darstellen muss.
   *
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  public void insertFieldFromTrafoDialog(List<String> fieldNames, final String buttonName,
      ConfigThingy trafoConf)
  {
    TrafoDialogParameters params = new TrafoDialogParameters();
    params.conf = new ConfigThingy("Func");
    params.conf.addChild(trafoConf);
    params.isValid = true;
    params.fieldNames = fieldNames;
    // params.closeAction = event -> {
    // TrafoDialog dialog = (TrafoDialog) event.getSource();
    // TrafoDialogParameters status = dialog.getExitStatus();
    // if (status.isValid)
    // {
    // try
    // {
    // getDocumentController().replaceSelectionWithTrafoField(status.conf, buttonName);
    // } catch (Exception x)
    // {
    // LOGGER.error("", x);
    // }
    // }
    // };

    // try
    // {
    // TrafoDialogFactory.createDialog(params).show(
    // L.m("Spezialfeld %1 einfügen", buttonName), null);
    // }
    // catch (UnavailableException e)
    // {
    // LOGGER.error(L.m("Das darf nicht passieren!"));
    // }
  }

  private static class CalcCellQueryResults implements QueryResults
  {
    /**
     * Bildet einen Spaltennamen auf den Index in dem zu dem Datensatz gehörenden String[]-Array ab.
     */
    private Map<String, Integer> mapColumnNameToIndex;

    private List<Dataset> datasets = new ArrayList<>();

    @Override
    public int size()
    {
      return datasets.size();
    }

    @Override
    public Iterator<Dataset> iterator()
    {
      return datasets.iterator();
    }

    @Override
    public boolean isEmpty()
    {
      return datasets.isEmpty();
    }

    public void setColumnNameToIndexMap(Map<String, Integer> mapColumnNameToIndex)
    {
      this.mapColumnNameToIndex = mapColumnNameToIndex;
    }

    public void addDataset(String[] data)
    {
      datasets.add(new MyDataset(data));
    }

    private class MyDataset implements Dataset
    {
      private String[] data;

      public MyDataset(String[] data)
      {
        this.data = data;
      }

      @Override
      public String get(String columnName) throws ColumnNotFoundException
      {
        Number idx = mapColumnNameToIndex.get(columnName);
        if (idx == null)
          throw new ColumnNotFoundException(L.m("Spalte %1 existiert nicht!", columnName));
        return data[idx.intValue()];
      }

      @Override
      public String getKey()
      {
        return "key";
      }

    }

  }
}
