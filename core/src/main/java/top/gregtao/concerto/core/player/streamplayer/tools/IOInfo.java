package top.gregtao.concerto.core.player.streamplayer.tools;

public class IOInfo {

	/**
	 * Returns the extension of file(without (.)) for example <b>(ai.mp3)->(mp3)</b>
	 * and to lowercase (Mp3 -> mp3)
	 *
	 * @param path The File absolute path
	 *
	 * @return the File extension
	 */
	public static String getFileExtension(final String path) {
		int i = path.lastIndexOf('.'); // characters contained before (.)

		 // if the name is not empty
		if (i > 0 && i < path.length() - 1)
			return path.substring(i + 1).toLowerCase();
		return null;
	}

}
