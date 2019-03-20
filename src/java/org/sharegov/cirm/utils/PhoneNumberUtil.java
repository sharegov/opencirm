package org.sharegov.cirm.utils;

/**
 * PhoneNumberUtil provides static methods for phone data to display formatting (305-333-4444#ext) 
 * and external system phone number normalization (3053334444#ext) for db storage.
 * 
 * @author Thomas Hilpold
 */
public class PhoneNumberUtil {

	/**
	 * Formats a cirm phone number into 311center preferred 305-111-2222(#ext) format. <br>
	 * Accepts any format including multiple comma space separated and # extensions.<br>
	 * Use to convert json & storage format to output for PDF reports, emails, etc.<br>
	 * <br>
	 * (All json sent to UI/interfaces/live reporting should be in cirm db storage format 3053334444.)<br>
	 * <br>
	 * @param phone in cirm storage format, but various formats accepted including null.
	 * @return formatted phone number if 10 digits, original nr if<>10 digits, null or empty string.
	 */
	public static String formatPhoneDataForDisplay(String data) {
		String result;
		if (data == null || data.isEmpty()) return data;		
		String[] phoneNums = data.split(",");
		for (int i = 0; i < phoneNums.length; i++) {
			phoneNums[i] = formatOnePhoneNumber(phoneNums[i]);
		}		
		result = "";
		for (int i = 0; i < phoneNums.length; i++) {
			result += phoneNums[i];
			if (i != phoneNums.length-1) {
				result += ", ";
			}
		}
		return result;
	}
	
	static String formatOnePhoneNumber(String oneNum) {
		if (oneNum == null || oneNum.isEmpty()) return oneNum;
		String result;
		String[] numExt = oneNum.split("#");
		String number = numExt[0];
		String ext = null;
		if (numExt.length > 1) {
			ext = onlyDigits(numExt[1]);
		}
		number = onlyDigits(number);
		if (number.length() == 10) {
			result = number.substring(0, 3) + "-" + number.substring(3, 6) + "-" + number.substring(6);
		} else {
			result = number;
		}
		if (ext != null && !ext.isEmpty()) {
			result += "#" + ext.trim();
		}
		return result;
	}
	
	/**
	 * Normalizes a single given US phone number value to "3051112222", or "9991112222#3333" in case an extension is provided.
	 * Commas in the parameter will be ignored as as single number is expected.
	 * 
	 * @param value null or empty allowed and returned
	 * @return the normalized phone number value
	 */
	public static String normalizeOnePhoneNumber(String oneNum) {
		if (oneNum == null || oneNum.isEmpty()) return oneNum;
		String result;
		String[] numExt = oneNum.split("#");
		String number = numExt[0];
		String ext = null;
		if (numExt.length > 1) {
			ext = onlyDigits(numExt[1]);
		}
		if (numExt.length > 2) {
			System.err.println("Error multi extension number: " + oneNum + " Second # will be ignored.");
		}
		number = onlyDigits(number);
		//Remove 1 at start (country code usa)
		if (number.startsWith("1") && number.length() > 10) number = number.substring(1);
		result = number;
		if (number.length() != 10) {
			System.err.println("Number != 10 digits " + number);
		}
		if (ext != null && !ext.isEmpty()) {
			result += "#" + ext;
		}
		return result;
	}

	/**
	 * Returns a string consisting of only the digits in any.
	 * 
	 * @param any null allowed
	 * @return digits, empty string o null if param.
	 */
	public static String onlyDigits(String any) {
		if (any == null) return null;
		String result = "";
		for (char c : any.toCharArray()) {
			if (Character.isDigit(c)) {
				result += c;
			}
		}
		return result;
	}
}
