package org.genepattern.gpge.ui.util;

import java.awt.Component;
import java.awt.FileDialog;
import java.awt.Graphics;
import java.awt.Insets;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.border.Border;

import org.genepattern.gpge.GenePattern;
import org.genepattern.gpge.ui.maindisplay.GPGE;

public class GUIUtil {
	static JFileChooser fileChooser;

	private GUIUtil() {
	}

	private static File showFileDialog(int mode) {
		return showFileDialog(mode, null);
	}

	private static File showFileDialog(int mode, File selectedFile) {
		FileDialog fc = new FileDialog(GenePattern.getDialogParent(),
				"GenePattern", mode);
		if (selectedFile != null) {
			fc.setDirectory(selectedFile.getPath());
			fc.setFile(selectedFile.getName());
		}
		fc.setModal(true);
		fc.show();
		String f = fc.getFile();
		String directory = fc.getDirectory();
		if (f != null) {
			File file = new File(directory, f);
			if (mode == FileDialog.SAVE) {
				if (!overwriteFile(file)) {
					return file;
				}
				return null;
			} else {
				return file;
			}

		}
		return null;
	}

	public static File showOpenDialog() {
		if (GPGE.RUNNING_ON_MAC) {
			return showFileDialog(FileDialog.LOAD);
		} else {
			if (fileChooser == null) {
				fileChooser = new JFileChooser();
			}
			if (fileChooser.showOpenDialog(GenePattern.getDialogParent()) == JFileChooser.APPROVE_OPTION) {
				return fileChooser.getSelectedFile();
			}
			return null;
		}
	}

	public static File showSaveDialog() {
		return showSaveDialog(null);
	}

	public static File showSaveDialog(File selectedFile) {
		if (GPGE.RUNNING_ON_MAC) {
			return showFileDialog(FileDialog.SAVE, selectedFile);
		} else {
			if (fileChooser == null) {
				fileChooser = new JFileChooser();
			}
			fileChooser.setSelectedFile(selectedFile);
			if (fileChooser.showSaveDialog(GenePattern.getDialogParent()) == JFileChooser.APPROVE_OPTION) {
				final File outputFile = fileChooser.getSelectedFile();
				if (!overwriteFile(outputFile)) {
					return null;
				} else {
					return outputFile;
				}
			}
			return null;
		}
	}

	public static boolean overwriteFile(File f) {
		if (!f.exists()) {
			return true;
		}
		String message = "An item named "
				+ f.getName()
				+ " already exists in this location.\nDo you want to replace it with the one that you are saving?";
		if (JOptionPane.showOptionDialog(GenePattern.getDialogParent(),
				message, null, JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE, GenePattern.getIcon(),
				new Object[] { "Replace", "Cancel" }, "Cancel") != JOptionPane.YES_OPTION) {
			return false;
		}
		return true;
	}

	/**
	 * Creates a wrapped border with the given insets
	 * @param b
	 * @param left
	 * @param top
	 * @param right
	 * @param bottom
	 * @return
	 */
	public static Border createBorder(final Border b, final int left, final int top,
			final int right, final int bottom) {
		return new javax.swing.border.Border() {
			public Insets getBorderInsets(java.awt.Component c) {
				Insets i = b.getBorderInsets(c);
				if (left >= 0) {
					i.left = left;
				}
				if (top >= 0) {
					i.top = top;
				}
				if (right >= 0) {
					i.right = right;
				}
				if (bottom >= 0) {
					i.bottom = bottom;
				}
	
				return i;
			}
	
			public boolean isBorderOpaque() {
				return b.isBorderOpaque();
			}
	
			public void paintBorder(Component c, Graphics g, int x, int y,
					int width, int height) {
				b.paintBorder(c, g, x, y, width, height);
			}
	
		};
	}

}
