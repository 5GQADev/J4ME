/*
 *  Copyright 2018 Nikita Shakarun
 *  Copyright 2021 Yury Kharchenko
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package javax.microedition.lcdui.keyboard;

import android.util.SparseIntArray;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import androidx.collection.SparseArrayCompat;

import ru.playsoftware.j2meloader.config.ProfileModel;

import static javax.microedition.lcdui.Canvas.*;

public class KeyMapper {
	public static final int KEY_OPTIONS_MENU = 0;
	public static final int SE_KEY_SPECIAL_GAMING_A = -13;
	public static final int SE_KEY_SPECIAL_GAMING_B = -14;

	private static final int DEFAULT_LAYOUT = 0;
	private static final int SIEMENS_LAYOUT = 1;
	private static final int MOTOROLA_LAYOUT = 2;
	private static final int CUSTOM_LAYOUT = 3;

	private static final int SIEMENS_KEY_UP = -59;
	private static final int SIEMENS_KEY_DOWN = -60;
	private static final int SIEMENS_KEY_LEFT = -61;
	private static final int SIEMENS_KEY_RIGHT = -62;
	private static final int SIEMENS_KEY_SOFT_LEFT = -1;
	private static final int SIEMENS_KEY_SOFT_RIGHT = -4;
	private static final int MOTOROLA_KEY_UP = -1;
	private static final int MOTOROLA_KEY_DOWN = -6;
	private static final int MOTOROLA_KEY_LEFT = -2;
	private static final int MOTOROLA_KEY_RIGHT = -5;
	private static final int MOTOROLA_KEY_FIRE = -20;
	private static final int MOTOROLA_KEY_SOFT_LEFT = -21;
	private static final int MOTOROLA_KEY_SOFT_RIGHT = -22;

	private static final SparseArrayCompat<String> keyCodeToKeyName = new SparseArrayCompat<>();
	private static final SparseIntArray keyCodeToCustom = new SparseIntArray();
	private static final SparseIntArray keyCodeToGameAction = new SparseIntArray();
	private static final SparseIntArray gameActionToKeyCode = new SparseIntArray();
	private static SparseIntArray androidToMIDP;
	private static int layoutType;
	private static final Map<ClassLoader, SessionMapping> SESSION_MAPPINGS = new ConcurrentHashMap<>();

	static {
		keyCodeToGameAction.put(KEY_NUM0, 0);
		keyCodeToGameAction.put(KEY_NUM1, 0);
		keyCodeToGameAction.put(KEY_NUM2, UP);
		keyCodeToGameAction.put(KEY_NUM3, 0);
		keyCodeToGameAction.put(KEY_NUM4, LEFT);
		keyCodeToGameAction.put(KEY_NUM5, FIRE);
		keyCodeToGameAction.put(KEY_NUM6, RIGHT);
		keyCodeToGameAction.put(KEY_NUM7, GAME_A);
		keyCodeToGameAction.put(KEY_NUM8, DOWN);
		keyCodeToGameAction.put(KEY_NUM9, GAME_B);
		keyCodeToGameAction.put(KEY_STAR, GAME_C);
		keyCodeToGameAction.put(KEY_POUND, GAME_D);
		mapKeyCode(KEY_UP, UP, "UP");
		mapKeyCode(KEY_DOWN, DOWN, "DOWN");
		mapKeyCode(KEY_LEFT, LEFT, "LEFT");
		mapKeyCode(KEY_RIGHT, RIGHT, "RIGHT");
		mapKeyCode(KEY_FIRE, FIRE, "SELECT");
		mapKeyCode(KEY_SOFT_LEFT, 0, "SOFT1");
		mapKeyCode(KEY_SOFT_RIGHT, 0, "SOFT2");
		mapKeyCode(KEY_CLEAR, 0, "CLEAR");
		mapKeyCode(KEY_SEND, 0, "SEND");
		mapKeyCode(KEY_END, 0, "END");

		mapGameAction(UP, KEY_UP);
		mapGameAction(LEFT, KEY_LEFT);
		mapGameAction(RIGHT, KEY_RIGHT);
		mapGameAction(DOWN, KEY_DOWN);
		mapGameAction(FIRE, KEY_FIRE);
		mapGameAction(GAME_A, KEY_NUM7);
		mapGameAction(GAME_B, KEY_NUM9);
		mapGameAction(GAME_C, KEY_STAR);
		mapGameAction(GAME_D, KEY_POUND);
	}

	private static void remapKeys() {
		if (layoutType == SIEMENS_LAYOUT) {
			keyCodeToCustom.put(KEY_LEFT, SIEMENS_KEY_LEFT);
			keyCodeToCustom.put(KEY_RIGHT, SIEMENS_KEY_RIGHT);
			keyCodeToCustom.put(KEY_UP, SIEMENS_KEY_UP);
			keyCodeToCustom.put(KEY_DOWN, SIEMENS_KEY_DOWN);
			keyCodeToCustom.put(KEY_SOFT_LEFT, SIEMENS_KEY_SOFT_LEFT);
			keyCodeToCustom.put(KEY_SOFT_RIGHT, SIEMENS_KEY_SOFT_RIGHT);

			mapGameAction(LEFT, SIEMENS_KEY_LEFT);
			mapGameAction(RIGHT, SIEMENS_KEY_RIGHT);
			mapGameAction(UP, SIEMENS_KEY_UP);
			mapGameAction(DOWN, SIEMENS_KEY_DOWN);

			mapKeyCode(SIEMENS_KEY_UP, UP, "UP");
			mapKeyCode(SIEMENS_KEY_DOWN, DOWN, "DOWN");
			mapKeyCode(SIEMENS_KEY_LEFT, LEFT, "LEFT");
			mapKeyCode(SIEMENS_KEY_RIGHT, RIGHT, "RIGHT");
			mapKeyCode(SIEMENS_KEY_SOFT_LEFT, 0, "SOFT1");
			mapKeyCode(SIEMENS_KEY_SOFT_RIGHT, 0, "SOFT2");
		} else if (layoutType == MOTOROLA_LAYOUT) {
			keyCodeToCustom.put(KEY_UP, MOTOROLA_KEY_UP);
			keyCodeToCustom.put(KEY_DOWN, MOTOROLA_KEY_DOWN);
			keyCodeToCustom.put(KEY_LEFT, MOTOROLA_KEY_LEFT);
			keyCodeToCustom.put(KEY_RIGHT, MOTOROLA_KEY_RIGHT);
			keyCodeToCustom.put(KEY_FIRE, MOTOROLA_KEY_FIRE);
			keyCodeToCustom.put(KEY_SOFT_LEFT, MOTOROLA_KEY_SOFT_LEFT);
			keyCodeToCustom.put(KEY_SOFT_RIGHT, MOTOROLA_KEY_SOFT_RIGHT);

			mapGameAction(LEFT, MOTOROLA_KEY_LEFT);
			mapGameAction(RIGHT, MOTOROLA_KEY_RIGHT);
			mapGameAction(UP, MOTOROLA_KEY_UP);
			mapGameAction(DOWN, MOTOROLA_KEY_DOWN);
			mapGameAction(FIRE, MOTOROLA_KEY_FIRE);

			mapKeyCode(MOTOROLA_KEY_UP, UP, "UP");
			mapKeyCode(MOTOROLA_KEY_DOWN, DOWN, "DOWN");
			mapKeyCode(MOTOROLA_KEY_LEFT, LEFT, "LEFT");
			mapKeyCode(MOTOROLA_KEY_RIGHT, RIGHT, "RIGHT");
			mapKeyCode(MOTOROLA_KEY_FIRE, FIRE, "SELECT");
			mapKeyCode(MOTOROLA_KEY_SOFT_LEFT, 0, "SOFT1");
			mapKeyCode(MOTOROLA_KEY_SOFT_RIGHT, 0, "SOFT2");
		}
	}

	private static void mapKeyCode(int midpKeyCode, int gameAction, String keyName) {
		keyCodeToGameAction.put(midpKeyCode, gameAction);
		keyCodeToKeyName.put(midpKeyCode, keyName);
	}

	private static void mapGameAction(int gameAction, int keyCode) {
		gameActionToKeyCode.put(gameAction, keyCode);
	}

	/** Register an immutable input mapping for one MIDlet runtime. */
	public static void registerSession(ClassLoader loader, ProfileModel profile) {
		if (loader != null && profile != null) SESSION_MAPPINGS.put(loader, new SessionMapping(profile));
	}

	/** Remove the input mapping when its MIDlet runtime exits. */
	public static void releaseSession(ClassLoader loader) {
		if (loader != null) SESSION_MAPPINGS.remove(loader);
	}

	public static int convertAndroidKeyCode(ClassLoader loader, int keyCode, KeyEvent event) {
		SessionMapping mapping = loader == null ? null : SESSION_MAPPINGS.get(loader);
		return mapping == null ? convertAndroidKeyCode(keyCode, event) : mapping.convertAndroidKeyCode(keyCode, event);
	}

	public static int convertKeyCode(ClassLoader loader, int keyCode) {
		SessionMapping mapping = loader == null ? null : SESSION_MAPPINGS.get(loader);
		return mapping == null ? convertKeyCode(keyCode) : mapping.convertKeyCode(keyCode);
	}

	public static int getKeyCode(ClassLoader loader, int gameAction) {
		SessionMapping mapping = loader == null ? null : SESSION_MAPPINGS.get(loader);
		return mapping == null ? getKeyCode(gameAction) : mapping.gameActionToKeyCode.get(gameAction, Integer.MAX_VALUE);
	}

	public static int getGameAction(ClassLoader loader, int keyCode) {
		SessionMapping mapping = loader == null ? null : SESSION_MAPPINGS.get(loader);
		return mapping == null ? getGameAction(keyCode) : mapping.keyCodeToGameAction.get(keyCode, 0);
	}

	public static String getKeyName(ClassLoader loader, int keyCode) {
		SessionMapping mapping = loader == null ? null : SESSION_MAPPINGS.get(loader);
		return mapping == null ? getKeyName(keyCode) : mapping.getKeyName(keyCode);
	}

	/** Per-session copy of all mutable mapping state; the legacy static map remains for single-player mode. */
	private static final class SessionMapping {
		final int layout;
		final SparseIntArray androidToMidp;
		final SparseIntArray custom = new SparseIntArray();
		final SparseIntArray keyCodeToGameAction = new SparseIntArray();
		final SparseIntArray gameActionToKeyCode = new SparseIntArray();
		final SparseArrayCompat<String> names = new SparseArrayCompat<>();

		SessionMapping(ProfileModel profile) {
			layout = profile.keyCodesLayout;
			androidToMidp = getDefaultKeyMap();
			if (profile.keyMappings != null) {
				for (int i = 0; i < profile.keyMappings.size(); i++) androidToMidp.put(profile.keyMappings.keyAt(i), profile.keyMappings.valueAt(i));
			}
			keyCodeToGameAction.put(KEY_NUM0, 0); keyCodeToGameAction.put(KEY_NUM1, 0);
			keyCodeToGameAction.put(KEY_NUM2, UP); keyCodeToGameAction.put(KEY_NUM3, 0);
			keyCodeToGameAction.put(KEY_NUM4, LEFT); keyCodeToGameAction.put(KEY_NUM5, FIRE);
			keyCodeToGameAction.put(KEY_NUM6, RIGHT); keyCodeToGameAction.put(KEY_NUM7, GAME_A);
			keyCodeToGameAction.put(KEY_NUM8, DOWN); keyCodeToGameAction.put(KEY_NUM9, GAME_B);
			keyCodeToGameAction.put(KEY_STAR, GAME_C); keyCodeToGameAction.put(KEY_POUND, GAME_D);
			mapKey(KEY_UP, UP, "UP"); mapKey(KEY_DOWN, DOWN, "DOWN"); mapKey(KEY_LEFT, LEFT, "LEFT");
			mapKey(KEY_RIGHT, RIGHT, "RIGHT"); mapKey(KEY_FIRE, FIRE, "SELECT");
			mapKey(KEY_SOFT_LEFT, 0, "SOFT1"); mapKey(KEY_SOFT_RIGHT, 0, "SOFT2");
			mapKey(KEY_CLEAR, 0, "CLEAR"); mapKey(KEY_SEND, 0, "SEND"); mapKey(KEY_END, 0, "END");
			mapAction(UP, KEY_UP); mapAction(LEFT, KEY_LEFT); mapAction(RIGHT, KEY_RIGHT); mapAction(DOWN, KEY_DOWN);
			mapAction(FIRE, KEY_FIRE); mapAction(GAME_A, KEY_NUM7); mapAction(GAME_B, KEY_NUM9); mapAction(GAME_C, KEY_STAR); mapAction(GAME_D, KEY_POUND);
			if (layout == SIEMENS_LAYOUT) {
				custom.put(KEY_LEFT, SIEMENS_KEY_LEFT); custom.put(KEY_RIGHT, SIEMENS_KEY_RIGHT); custom.put(KEY_UP, SIEMENS_KEY_UP); custom.put(KEY_DOWN, SIEMENS_KEY_DOWN);
				custom.put(KEY_SOFT_LEFT, SIEMENS_KEY_SOFT_LEFT); custom.put(KEY_SOFT_RIGHT, SIEMENS_KEY_SOFT_RIGHT);
				mapAction(LEFT, SIEMENS_KEY_LEFT); mapAction(RIGHT, SIEMENS_KEY_RIGHT); mapAction(UP, SIEMENS_KEY_UP); mapAction(DOWN, SIEMENS_KEY_DOWN);
				mapKey(SIEMENS_KEY_UP, UP, "UP"); mapKey(SIEMENS_KEY_DOWN, DOWN, "DOWN"); mapKey(SIEMENS_KEY_LEFT, LEFT, "LEFT"); mapKey(SIEMENS_KEY_RIGHT, RIGHT, "RIGHT");
				mapKey(SIEMENS_KEY_SOFT_LEFT, 0, "SOFT1"); mapKey(SIEMENS_KEY_SOFT_RIGHT, 0, "SOFT2");
			} else if (layout == MOTOROLA_LAYOUT) {
				custom.put(KEY_UP, MOTOROLA_KEY_UP); custom.put(KEY_DOWN, MOTOROLA_KEY_DOWN); custom.put(KEY_LEFT, MOTOROLA_KEY_LEFT); custom.put(KEY_RIGHT, MOTOROLA_KEY_RIGHT);
				custom.put(KEY_FIRE, MOTOROLA_KEY_FIRE); custom.put(KEY_SOFT_LEFT, MOTOROLA_KEY_SOFT_LEFT); custom.put(KEY_SOFT_RIGHT, MOTOROLA_KEY_SOFT_RIGHT);
				mapAction(LEFT, MOTOROLA_KEY_LEFT); mapAction(RIGHT, MOTOROLA_KEY_RIGHT); mapAction(UP, MOTOROLA_KEY_UP); mapAction(DOWN, MOTOROLA_KEY_DOWN); mapAction(FIRE, MOTOROLA_KEY_FIRE);
				mapKey(MOTOROLA_KEY_UP, UP, "UP"); mapKey(MOTOROLA_KEY_DOWN, DOWN, "DOWN"); mapKey(MOTOROLA_KEY_LEFT, LEFT, "LEFT"); mapKey(MOTOROLA_KEY_RIGHT, RIGHT, "RIGHT");
				mapKey(MOTOROLA_KEY_FIRE, FIRE, "SELECT"); mapKey(MOTOROLA_KEY_SOFT_LEFT, 0, "SOFT1"); mapKey(MOTOROLA_KEY_SOFT_RIGHT, 0, "SOFT2");
			}
		}
		void mapKey(int code, int action, String name) { keyCodeToGameAction.put(code, action); names.put(code, name); }
		void mapAction(int action, int code) { gameActionToKeyCode.put(action, code); }
		int convertAndroidKeyCode(int code, KeyEvent event) {
			if (!event.isShiftPressed()) { int mapped = androidToMidp.get(code, 0); if (mapped != 0) return mapped; }
			return event.getUnicodeChar() & KeyCharacterMap.COMBINING_ACCENT_MASK;
		}
		int convertKeyCode(int code) { return layout == DEFAULT_LAYOUT ? code : custom.get(code, code); }
		String getKeyName(int code) { String name = names.get(code); if (name == null && Character.isValidCodePoint(code)) name = new String(Character.toChars(code)); return name; }
	}

	public static int convertAndroidKeyCode(int keyCode, KeyEvent event) {
		if (!event.isShiftPressed()) {
			int map = androidToMIDP.get(keyCode, 0);
			if (map != 0) {
				return map;
			}
		}
		// TODO: 27.06.2021 ignored ascent char combination
		return event.getUnicodeChar() & KeyCharacterMap.COMBINING_ACCENT_MASK;
	}

	public static int convertKeyCode(int keyCode) {
		if (layoutType == DEFAULT_LAYOUT) {
			return keyCode;
		}
		return keyCodeToCustom.get(keyCode, keyCode);
	}

	public static void setKeyMapping(ProfileModel params) {
		layoutType = params.keyCodesLayout;
		SparseIntArray map = getDefaultKeyMap();
		SparseIntArray customKeyMap = params.keyMappings;
		if (customKeyMap != null) {
			for (int i = 0, size = customKeyMap.size(); i < size; i++) {
				map.put(customKeyMap.keyAt(i), customKeyMap.valueAt(i));
			}
		}
		androidToMIDP = map;
		remapKeys();
	}

	public static int getKeyCode(int gameAction) {
		return gameActionToKeyCode.get(gameAction, Integer.MAX_VALUE);
	}

	public static int getGameAction(int keyCode) {
		return keyCodeToGameAction.get(keyCode, 0);
	}

	public static String getKeyName(int keyCode) {
		String name = keyCodeToKeyName.get(keyCode);
		if (name == null) {
			if (Character.isValidCodePoint(keyCode)) {
				name = new String(Character.toChars(keyCode));
			}
		}
		return name;
	}

	public static SparseIntArray getDefaultKeyMap() {
		SparseIntArray map = new SparseIntArray();
		map.append(KeyEvent.KEYCODE_SOFT_LEFT, KEY_SOFT_LEFT);
		map.append(KeyEvent.KEYCODE_SOFT_RIGHT, KEY_SOFT_RIGHT);
		map.append(KeyEvent.KEYCODE_BACK, KEY_OPTIONS_MENU);
		map.append(KeyEvent.KEYCODE_CALL, KEY_SEND);
		map.append(KeyEvent.KEYCODE_ENDCALL, KEY_END);
		map.append(KeyEvent.KEYCODE_0, KEY_NUM0);
		map.append(KeyEvent.KEYCODE_1, KEY_NUM1);
		map.append(KeyEvent.KEYCODE_2, KEY_NUM2);
		map.append(KeyEvent.KEYCODE_3, KEY_NUM3);
		map.append(KeyEvent.KEYCODE_4, KEY_NUM4);
		map.append(KeyEvent.KEYCODE_5, KEY_NUM5);
		map.append(KeyEvent.KEYCODE_6, KEY_NUM6);
		map.append(KeyEvent.KEYCODE_7, KEY_NUM7);
		map.append(KeyEvent.KEYCODE_8, KEY_NUM8);
		map.append(KeyEvent.KEYCODE_9, KEY_NUM9);
		map.append(KeyEvent.KEYCODE_STAR, KEY_STAR);
		map.append(KeyEvent.KEYCODE_POUND, KEY_POUND);
		map.append(KeyEvent.KEYCODE_DPAD_UP, KEY_UP);
		map.append(KeyEvent.KEYCODE_DPAD_DOWN, KEY_DOWN);
		map.append(KeyEvent.KEYCODE_DPAD_LEFT, KEY_LEFT);
		map.append(KeyEvent.KEYCODE_DPAD_RIGHT, KEY_RIGHT);
		map.append(KeyEvent.KEYCODE_ENTER, KEY_FIRE);
		map.append(KeyEvent.KEYCODE_DEL, KEY_CLEAR);
		return map;
	}
}
