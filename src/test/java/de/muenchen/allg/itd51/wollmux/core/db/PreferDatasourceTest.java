package de.muenchen.allg.itd51.wollmux.core.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import de.muenchen.allg.itd51.wollmux.core.db.mock.MockDataset;
import de.muenchen.allg.itd51.wollmux.core.db.mock.MockDatasource;
import de.muenchen.allg.itd51.wollmux.core.parser.ConfigThingy;
import de.muenchen.allg.itd51.wollmux.core.parser.ConfigurationErrorException;

class PreferDatasourceTest
{

  @Test
  void testPreferDatasource() throws Exception
  {
    Map<String, Datasource> nameToDatasource = new HashMap<>();
    nameToDatasource.put("mock", new MockDatasource());
    nameToDatasource.put("mock2", new MockDatasource("mock2", List.of("column"),
        List.of(new MockDataset("ds3", "column", "value3"), new MockDataset("ds", "column", "value4"))));
    Datasource ds = new PreferDatasource(nameToDatasource,
        new ConfigThingy("", "NAME \"prefer\" SOURCE \"mock\" OVER \"mock2\""), null);
    assertEquals("prefer", ds.getName());
    assertEquals(List.of("column"), ds.getSchema());
    QueryResults results = ds.getContents();
    assertEquals(0, results.size());
    results = ds.getDatasetsByKey(List.of("ds", "ds3"));
    assertEquals(2, results.size());
    results = ds.find(List.of(new QueryPart("column", "value3")));
    assertEquals(1, results.size());
    results = ds.find(List.of(new QueryPart("column", "value")));
    assertEquals(1, results.size());
  }

  @Test
  void testInvalidPreferDatasource() throws Exception
  {
    Map<String, Datasource> nameToDatasource = new HashMap<>();
    nameToDatasource.put("mock", new MockDatasource());
    nameToDatasource.put("mock2", new MockDatasource("mock2", List.of("column"), List.of()));
    assertThrows(ConfigurationErrorException.class,
        () -> new PreferDatasource(nameToDatasource, new ConfigThingy("", "NAME \"prefer\" SOURCE \"mock\""), null));
    assertThrows(ConfigurationErrorException.class,
        () -> new PreferDatasource(nameToDatasource, new ConfigThingy("", "NAME \"prefer\" OVER \"mock\""), null));
    assertThrows(ConfigurationErrorException.class, () -> new PreferDatasource(
        nameToDatasource,
        new ConfigThingy("", "NAME \"prefer\" SOURCE \"mock\" OVER \"unknown\""), null));
    assertThrows(ConfigurationErrorException.class, () -> new PreferDatasource(
        nameToDatasource,
        new ConfigThingy("", "NAME \"prefer\" SOURCE \"unknown\" OVER \"mock\""), null));

    nameToDatasource.put("mock2", new MockDatasource("mock2", List.of("column1", "column2"), List.of()));
    assertThrows(ConfigurationErrorException.class, () -> new PreferDatasource(nameToDatasource,
        new ConfigThingy("", "NAME \"prefer\" SOURCE \"mock\" OVER \"mock2\""), null));

    nameToDatasource.put("mock",
        new MockDatasource("mock", List.of("column1", "column2", "column3", "column4"), List.of()));
    assertThrows(ConfigurationErrorException.class, () -> new PreferDatasource(nameToDatasource,
        new ConfigThingy("", "NAME \"prefer\" SOURCE \"mock\" OVER \"mock2\""), null));
  }

  @Test
  void testPreferDatasourceResults() throws Exception
  {
    Map<String, Datasource> nameToDatasource = new HashMap<>();
    nameToDatasource.put("mock", new MockDatasource("mock", List.of("column"),
        List.of(new MockDataset("ds", "column", "value"))));
    nameToDatasource.put("mock2", new MockDatasource("mock2", List.of("column"),
        List.of(new MockDataset("ds", "column", "value3"), new MockDataset("ds1", "column", "value4"))));
    Datasource ds = new PreferDatasource(nameToDatasource,
        new ConfigThingy("", "NAME \"prefer\" SOURCE \"mock\" OVER \"mock2\""), null);
    QueryResults results = ds.getDatasetsByKey(List.of("ds"));
    assertFalse(results.isEmpty());
    assertEquals(1, results.size());
    Iterator<Dataset> iter = results.iterator();
    assertTrue(iter.hasNext());
    assertThrows(UnsupportedOperationException.class, () -> iter.remove());
    Dataset data = iter.next();
    assertEquals("value", data.get("column"));
    assertFalse(iter.hasNext());
    assertThrows(NoSuchElementException.class, () -> iter.next());

    results = ds.getDatasetsByKey(List.of("unknown"));
    assertTrue(results.isEmpty());

    results = ds.getDatasetsByKey(List.of("ds1"));
    Iterator<Dataset> iter2 = results.iterator();
    data = iter2.next();
    assertEquals("value4", data.get("column"));
  }
}
