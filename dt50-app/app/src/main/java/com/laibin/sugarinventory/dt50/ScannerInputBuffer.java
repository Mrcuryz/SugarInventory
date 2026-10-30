package com.laibin.sugarinventory.dt50;

import android.os.Handler;
import android.view.KeyEvent;

final class ScannerInputBuffer {
    interface Listener {
        void onScan(String value);
    }

    private static final long FLUSH_DELAY_MS = 180L;
    private static final long RESET_GAP_MS = 1200L;
    private static final int MAX_LENGTH = 256;

    private final StringBuilder buffer = new StringBuilder();
    private final Handler handler;
    private final Listener listener;
    private long lastCharacterAt;
    private final Runnable delayedFlush = this::flush;

    ScannerInputBuffer(Handler handler, Listener listener) {
        this.handler = handler;
        this.listener = listener;
    }

    boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            flush();
            return true;
        }
        int unicode = event.getUnicodeChar();
        if (unicode <= 0 || Character.isISOControl(unicode)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (lastCharacterAt > 0 && now - lastCharacterAt > RESET_GAP_MS) {
            buffer.setLength(0);
        }
        lastCharacterAt = now;
        if (buffer.length() < MAX_LENGTH) {
            buffer.append((char) unicode);
        }
        handler.removeCallbacks(delayedFlush);
        handler.postDelayed(delayedFlush, FLUSH_DELAY_MS);
        return true;
    }

    private void flush() {
        handler.removeCallbacks(delayedFlush);
        String value = buffer.toString().trim();
        buffer.setLength(0);
        lastCharacterAt = 0;
        if (!value.isEmpty()) {
            listener.onScan(value);
        }
    }
}
