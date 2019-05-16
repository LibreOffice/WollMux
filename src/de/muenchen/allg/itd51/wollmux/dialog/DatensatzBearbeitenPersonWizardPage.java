package de.muenchen.allg.itd51.wollmux.dialog;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.star.awt.XButton;
import com.sun.star.awt.XComboBox;
import com.sun.star.awt.XControl;
import com.sun.star.awt.XControlContainer;
import com.sun.star.awt.XTextComponent;
import com.sun.star.awt.XWindow;
import com.sun.star.uno.Exception;
import com.sun.star.uno.UnoRuntime;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.itd51.wollmux.core.db.ColumnNotFoundException;
import de.muenchen.allg.itd51.wollmux.core.db.DJDataset;
import de.muenchen.allg.itd51.wollmux.core.db.Dataset;

public class DatensatzBearbeitenPersonWizardPage extends DatensatzBearbeitenBaseWizardPage
{
  private static final Logger LOGGER = LoggerFactory
      .getLogger(DatensatzBearbeitenPersonWizardPage.class);

  public DatensatzBearbeitenPersonWizardPage(XWindow parentWindow, short pageId, DJDataset dataset,
      Dataset ldapDataset, List<String> dbSchema) throws Exception
  {
    super(pageId, parentWindow, "DatensatzBearbeitenPerson", dataset, ldapDataset, dbSchema);

    XControlContainer controlContainerPerson = UnoRuntime.queryInterface(XControlContainer.class,
        window);
    setControlContainer(controlContainerPerson);
    
    try
    {
      XComboBox anredeComboBox = UNO.XComboBox(controlContainerPerson.getControl("Anrede"));
      XTextComponent anredeTextComponent = UNO.XTextComponent(anredeComboBox);
      String anrede = dataset.get("Anrede");
      anredeTextComponent.setText(anrede);

      anredeComboBox.removeItems((short) 0, anredeComboBox.getItemCount());
      anredeComboBox.addItems(new String[] { "Herr", "Frau" }, (short) 0);

      anredeComboBox.addItemListener(itemListener);

      for (String columnName : dbSchema)
      {
        XControl xControl = controlContainerPerson.getControl(columnName);

        if (xControl == null)
          continue;

        XTextComponent xTextComponent = UNO.XTextComponent(xControl);

        if (xTextComponent == null)
          continue;

        xTextComponent.setText(dataset.get(columnName));
        
        if (isDifferentFromLdapDataset(columnName))
        {
          showAcceptLdapValueButton(columnName);
          super.setTextColor(xControl, 16711680); // rot
        }
      }
 
      for (XControl control : controlContainerPerson.getControls())
      {
        XTextComponent textComponent = UNO.XTextComponent(control);

        XButton xButton = UNO.XButton(control);

        if (textComponent != null)
          textComponent.addTextListener(textListener);

        if (xButton != null)
          xButton.addActionListener(buttonActionListener);
      }

    } catch (ColumnNotFoundException e)
    {
      LOGGER.error("", e);
    }
  }
}
