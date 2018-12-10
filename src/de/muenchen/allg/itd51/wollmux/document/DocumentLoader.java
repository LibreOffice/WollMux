package de.muenchen.allg.itd51.wollmux.document;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.sun.star.beans.PropertyState;
import com.sun.star.beans.PropertyValue;
import com.sun.star.io.XInputStream;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.XComponent;
import com.sun.star.uno.Exception;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.itd51.wollmux.core.util.L;

/**
 * Funktionen zum Laden und Einfügen von Dokumenten. Geladene Dokumente werden
 * gecacht.
 */
public class DocumentLoader
{
  private static final Logger LOGGER = LoggerFactory
    .getLogger(DocumentLoader.class);

  private static DocumentLoader instance;
  private LoadingCache<URL, ByteBuffer> cache;

  /**
   * Zugriff auf den DocumentLoader als Singleton.
   * 
   * @return Singleton-Instanz des DocumentLoaders
   */
  public static DocumentLoader getInstance()
  {
    if (instance == null)
    {
      instance = new DocumentLoader();
    }
    return instance;
  }

  private DocumentLoader()
  {
    cache = CacheBuilder.newBuilder()
      .maximumSize(50)
      .expireAfterAccess(8, TimeUnit.HOURS)
      .build(new CacheLoader<URL, ByteBuffer>()
      {
        @Override
        public ByteBuffer load(URL url) throws Exception
        {
          return downloadDocument(url);
        }
      });
  }

  private ByteBuffer downloadDocument(URL url)
  {
    byte[] buf = null;
    try (InputStream in = url.openStream())
    {
      buf = IOUtils.toByteArray(in);
    } catch (IOException e)
    {
      LOGGER.error(
        L.m("Die Vorlage mit der URL '%1' kann nicht geöffnet werden.", url),
        e);
    }

    return ByteBuffer.wrap(buf);
  }

  /**
   * Lädt ein Dokument und fügt es an der Stelle von target ein. target muss den
   * Service XDocumentInsertable unterstützen.
   * 
   * @param target
   * @param path   URL des Dokuments
   */
  public void insertDocument(Object target, String path)
  {
    try
    {
      ByteBuffer buf = cache.get(new URL(path));
      XInputStream in = new ByteBufferInputStream(buf);
      UNO.XDocumentInsertable(target).insertDocumentFromURL(path,
        new PropertyValue[] {
          new PropertyValue("InputStream", -1, in, PropertyState.DIRECT_VALUE),
          new PropertyValue("FilterName", -1, "StarOffice XML (Writer)",
            PropertyState.DIRECT_VALUE)
        });
    } catch (MalformedURLException | IllegalArgumentException
      | com.sun.star.io.IOException | ExecutionException e)
    {
      LOGGER.error("", e);
    }
  }
  
  /**
   * Lädt ein Dokument und öffnet es.
   * 
   * @param path        URL des Dokuments
   * @param asTemplate  behandelt das Dokument als Template
   * @param allowMacros erlaubt die Ausführung von Makros
   * 
   * @return
   */
  public XComponent loadDocument(String path, boolean asTemplate,
    boolean allowMacros)
  {
    try
    {
      ByteBuffer buf = cache.get(new URL(path));
      XInputStream in = new ByteBufferInputStream(buf);
      return UNO.loadComponentFromURL(path, asTemplate, allowMacros,
          new PropertyValue("InputStream", -1, in, PropertyState.DIRECT_VALUE),
          new PropertyValue("FilterName", -1, "StarOffice XML (Writer)",
            PropertyState.DIRECT_VALUE)
      );
    } catch (MalformedURLException | IllegalArgumentException
      | com.sun.star.io.IOException | ExecutionException e)
    {
      LOGGER.error("", e);
    }

    return null;
  }
}
