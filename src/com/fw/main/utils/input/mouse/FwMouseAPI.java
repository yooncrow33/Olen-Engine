package com.fw.main.utils.input.mouse;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public class FwMouseAPI {

    private MouseEvent rawEvent;

    public FwMouseAPI() {
    }

    public int getX() {
        return rawEvent != null ? rawEvent.getX() : 0;
    }

    public int getY() {
        return rawEvent != null ? rawEvent.getY() : 0;
    }

    public void setRawEvent(MouseEvent e) {
        this.rawEvent = e;
    }

    public void clear() {
        this.rawEvent = null;
    }

    /**
     * Returns which mouse button changed state (PRESSED, RELEASED, CLICKED).
     * NOTE: During mouseDragged, AWT returns NOBUTTON (0). Use isLeftButton() or isRightButton() to check held buttons.
     */
    public int getButton() {
        return rawEvent != null ? rawEvent.getButton() : MouseEvent.NOBUTTON;
    }

    /**
     * Returns true if the left mouse button is pressed or being held during a drag.
     */
    public boolean isLeftButton() {
        if (rawEvent == null) return false;
        return (rawEvent.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0 || rawEvent.getButton() == MouseEvent.BUTTON1;
    }

    /**
     * Returns true if the right mouse button is pressed or being held during a drag.
     */
    public boolean isRightButton() {
        if (rawEvent == null) return false;
        return (rawEvent.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 || rawEvent.getButton() == MouseEvent.BUTTON3;
    }

    public boolean isDoubleClick() {
        return rawEvent != null && rawEvent.getClickCount() == 2;
    }

    public boolean isPopupTrigger() {
        return rawEvent != null && rawEvent.isPopupTrigger();
    }

    public boolean isControlDown() {
        return rawEvent != null && rawEvent.isControlDown();
    }

    public boolean isShiftDown() {
        return rawEvent != null && rawEvent.isShiftDown();
    }

    public boolean isAltDown() {
        return rawEvent != null && rawEvent.isAltDown();
    }

    public boolean isMetaDown() {
        return rawEvent != null && rawEvent.isMetaDown();
    }

    public int getModifiersEx() {
        return rawEvent != null ? rawEvent.getModifiersEx() : 0;
    }

    public int getWheelRotation() {
        if (rawEvent instanceof MouseWheelEvent) {
            return ((MouseWheelEvent) rawEvent).getWheelRotation();
        }
        return 0;
    }

    public int getPreciseWheelRotation() {
        if (rawEvent instanceof MouseWheelEvent) {
            return (int) ((MouseWheelEvent) rawEvent).getPreciseWheelRotation();
        }
        return 0;
    }

    public void consume() {
        if (rawEvent != null) {
            rawEvent.consume();
        }
    }

    public boolean isConsumed() {
        return rawEvent != null && rawEvent.isConsumed();
    }
}