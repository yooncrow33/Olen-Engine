package com.fw.main.utils.input.mouse;

public interface MouseInterface {
    default void mouseClicked(FwMouseAPI e) {}
    default void mousePressed(FwMouseAPI e) {}
    default void mouseReleased(FwMouseAPI e) {}
    default void mouseEntered(FwMouseAPI e) {}
    default void mouseExited(FwMouseAPI e) {}
    default void mouseWheelMoved(FwMouseAPI e) {}

    /**
     * Invoked when a mouse button is pressed on a component and then dragged.
     */
    default void mouseDragged(FwMouseAPI e) {}

    /**
     * Invoked when the mouse cursor has been moved onto a component but no buttons have been pushed.
     */
    default void mouseMoved(FwMouseAPI e) {}
}