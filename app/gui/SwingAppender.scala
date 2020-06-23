package gui

import javax.swing._
import org.apache.logging.log4j.core.{Filter, Layout, LogEvent}
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.plugins.{Plugin, PluginAttribute, PluginElement, PluginFactory}

import scala.collection.mutable

object SwingAppender {
  private final val textAreas = mutable.ListBuffer[JTextArea]();

  def registerTextArea(textArea: JTextArea): Unit = textAreas += textArea

  @PluginFactory def createAppender(
    @PluginAttribute("name") name: String,
    @PluginElement("Layout") layout: Layout[_],
    @PluginElement("Filters") filter: Filter
  ): SwingAppender = {
    require(layout != null, "Misconfigured layout")
    require(name != null, "Misconfigured name")

    new SwingAppender(name, filter, layout)
  }

}

@Plugin(name = "SwingAppender", category = "Core", elementType = "appender", printObject = true)
class SwingAppender protected(
  name: String,
  filter: Filter,
  layout: Layout[_]
) extends AbstractAppender(name, filter, layout) {

  override def append(event: LogEvent): Unit = {
    val msg = new String(layout.toByteArray(event))
    SwingUtilities.invokeLater { () =>
      SwingAppender.textAreas.foreach { textArea =>
        if (textArea.getText.nonEmpty) {
          textArea.append("\n")
        }
        textArea.append(s"${msg.stripLineEnd}")
        textArea.setCaretPosition(Math.max(textArea.getText().lastIndexOf("\n") + 1, 0));
      }
    }
  }
}
