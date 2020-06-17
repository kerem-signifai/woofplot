package gui

import java.awt._

import javax.swing._
import javax.swing.text._

object WoofGUI extends Runnable {

  override def run(): Unit = {

    val frame: JFrame = new JFrame("WoofPlot")
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)

    val jLoggingConsole = new JTextArea(5, 0)
    jLoggingConsole.setLineWrap(false)
    jLoggingConsole.setWrapStyleWord(true)
    jLoggingConsole.setEditable(false)
    jLoggingConsole.setFont(new Font("Consolas", Font.BOLD, 16))
    jLoggingConsole.setMargin(new Insets(10, 10, 0, 10))

    val caret = jLoggingConsole.getCaret.asInstanceOf[DefaultCaret]
    caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE)

    val jConsoleScroll = new JScrollPane(jLoggingConsole)
    jConsoleScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS)
    jConsoleScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS)

    jConsoleScroll.getVerticalScrollBar.setUnitIncrement(20)
    jConsoleScroll.getHorizontalScrollBar.setUnitIncrement(40)

    SwingAppender.registerTextArea(jLoggingConsole)

    frame.add(jConsoleScroll)
    frame.pack()

    val screenSize = Toolkit.getDefaultToolkit.getScreenSize
    frame.setSize(
      (screenSize.width / 1.5).toInt,
      screenSize.height / 2
    )

    frame.setLocationRelativeTo(null)
    frame.setVisible(true)
  }
}
