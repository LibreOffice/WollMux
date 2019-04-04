package de.muenchen.allg.itd51.wollmux.dialog;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.star.awt.PosSize;
import com.sun.star.awt.XWindow;
import com.sun.star.ui.dialogs.XWizardController;
import com.sun.star.ui.dialogs.XWizardPage;

import de.muenchen.allg.itd51.wollmux.core.db.DJDataset;
import de.muenchen.allg.itd51.wollmux.core.db.Dataset;

public class DatensatzBearbeitenWizardController implements XWizardController
{
  private static final Logger LOGGER = LoggerFactory
      .getLogger(DatensatzBearbeitenWizardController.class);
  private static final int PAGE_COUNT = 3;
  private XWizardPage[] pages = new XWizardPage[PAGE_COUNT];
  private DJDataset dataset;
  private Dataset ldapDataset;
  private Set<String> dbSchema;

  protected static final short[] PATHS = { 0, 1, 2 };

  private enum PAGE_ID
  {
    PERSON,
    ORGA,
    FUSSZEILE
  }

  private String[] title = { "Person", "Orga", "Fusszeile" };

  public DatensatzBearbeitenWizardController(DJDataset dataset, Dataset ldapDataset, Set<String> dbSchema)
  {
    this.dataset = dataset;
    this.ldapDataset = ldapDataset;
    this.dbSchema = dbSchema;
  }

  @Override
  public boolean canAdvance()
  {
    return true;
  }

  @Override
  public boolean confirmFinish()
  {
    return true;
  }

  @Override
  public XWizardPage createPage(XWindow arg0, short arg1)
  {
    arg0.setPosSize(0, 0, 1000, 800, PosSize.POSSIZE);
    LOGGER.debug("createPage");
    XWizardPage page = null;
    try
    {
      switch (getPageId(arg1))
      {
      case PERSON:
        page = new DatensatzBearbeitenPersonWizardPage(arg0, arg1, dataset, ldapDataset, dbSchema);
        break;
        
      case ORGA:
        page = new DatensatzBearbeitenOrgaWizardPage(arg0, arg1, dataset, ldapDataset, dbSchema);
        break;
        
      case FUSSZEILE:
        page = new DatensatzBearbeitenFusszeileWizardPage(arg0, arg1, dataset, ldapDataset, dbSchema);
        break;
      }
      pages[arg1] = page;
    } catch (Exception ex)
    {
      LOGGER.error("Page {} konnte nicht erstellt werden", arg1);
      LOGGER.error("", ex);
    }
    return page;
  }

  @Override
  public String getPageTitle(short arg0)
  {
    return title[arg0];
  }

  @Override
  public void onActivatePage(short arg0)
  {
    pages[arg0].activatePage();
  }

  @Override
  public void onDeactivatePage(short arg0)
  {
    //not used
  }

  private PAGE_ID getPageId(short pageId)
  {
    return PAGE_ID.values()[pageId];
  }

}
