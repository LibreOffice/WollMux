package de.muenchen.allg.itd51.wollmux.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.star.awt.ItemEvent;
import com.sun.star.awt.TextEvent;
import com.sun.star.awt.XButton;
import com.sun.star.awt.XControlContainer;
import com.sun.star.awt.XRadioButton;
import com.sun.star.awt.XTextComponent;
import com.sun.star.awt.XWindow;
import com.sun.star.uno.Exception;
import com.sun.star.uno.UnoRuntime;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.itd51.wollmux.core.dialog.adapter.AbstractItemListener;
import de.muenchen.allg.itd51.wollmux.core.dialog.adapter.AbstractTextListener;
import de.muenchen.allg.itd51.wollmux.core.dialog.adapter.AbstractXWizardPage;

public class FormatWizardPage extends AbstractXWizardPage
{
  
  private static final Logger LOGGER = LoggerFactory.getLogger(FormatWizardPage.class);
  
  private final XRadioButton odt;
  private final XRadioButton pdf;
  private final XTextComponent name;
  private final XButton mailmerge;
  private final XButton special;
  
  enum FORMAT {
    ODT,
    PDF,
    NOTHING;
  }
  
  private final MailmergeWizardController controller;
  
  private final AbstractItemListener formatListener = new AbstractItemListener() {

    @Override
    public void itemStateChanged(ItemEvent event)
    {
      controller.activateNextButton(canAdvance());
    }
  };
  
  public FormatWizardPage(XWindow parentWindow, short pageId, MailmergeWizardController controller) throws Exception
  {
    super(pageId, parentWindow, "seriendruck_format");
    this.controller = controller;
    XControlContainer container = UnoRuntime.queryInterface(XControlContainer.class, window);
    odt = UNO.XRadio(container.getControl("odt"));
    odt.addItemListener(formatListener);
    pdf = UNO.XRadio(container.getControl("pdf"));
    pdf.addItemListener(formatListener);
    name = UNO.XTextComponent(container.getControl("name"));
    name.addTextListener(new AbstractTextListener()
    {
      
      @Override
      public void textChanged(TextEvent arg0)
      {
        controller.activateNextButton(canAdvance());
      }
    });
    mailmerge = UNO.XButton(container.getControl("mailmerge"));
    special = UNO.XButton(container.getControl("special"));
  }
  
  private FORMAT getSelectedFormat()
  {
    if (odt.getState())
    {
      return FORMAT.ODT;
    }
    if (pdf.getState())
    {
      return FORMAT.PDF;
    }
    return FORMAT.NOTHING;
  }
  
  private String getNamingTemplate()
  {
    return name.getText();
  }

  @Override
  public boolean canAdvance()
  {
    LOGGER.debug("canAdvance");
    return getSelectedFormat() != FORMAT.NOTHING && !getNamingTemplate().isEmpty();
  }

  @Override
  public boolean commitPage(short reason)
  {
    window.setVisible(false);
    LOGGER.debug("Fromat {}, Name {}", getSelectedFormat(), getNamingTemplate());
    return true;
  }
}
