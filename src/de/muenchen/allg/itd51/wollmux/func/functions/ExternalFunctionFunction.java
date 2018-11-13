package de.muenchen.allg.itd51.wollmux.func.functions;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.muenchen.allg.itd51.wollmux.WollMuxClassLoader;
import de.muenchen.allg.itd51.wollmux.core.functions.ExternalFunction;
import de.muenchen.allg.itd51.wollmux.core.functions.Function;
import de.muenchen.allg.itd51.wollmux.core.functions.FunctionConstants;
import de.muenchen.allg.itd51.wollmux.core.functions.Values;
import de.muenchen.allg.itd51.wollmux.core.parser.ConfigThingy;
import de.muenchen.allg.itd51.wollmux.core.parser.ConfigurationErrorException;
import de.muenchen.allg.itd51.wollmux.core.util.L;

public class ExternalFunctionFunction implements Function
{

  private static final Logger LOGGER = LoggerFactory
      .getLogger(ExternalFunctionFunction.class);

  private ExternalFunction func;

  public ExternalFunctionFunction(ConfigThingy conf)
      throws ConfigurationErrorException
  {
    func = new ExternalFunction(conf, WollMuxClassLoader.getClassLoader());
  }

  @Override
  public String[] parameters()
  {
    return func.parameters();
  }

  @Override
  public void getFunctionDialogReferences(Collection<String> set)
  {}

  @Override
  public String getString(Values parameters)
  {
    try
    {
      Object result = func.invoke(parameters);
      if (result == null)
        throw new Exception(
          L.m("Unbekannter Fehler beim Ausführen einer externen Funktion"));
      return result.toString();
    }
    catch (Exception e)
    {
      LOGGER.error("", e);
      return FunctionConstants.ERROR;
    }
  }

  @Override
  public boolean getBoolean(Values parameters)
  {
    return getString(parameters).equalsIgnoreCase("true");
  }
}