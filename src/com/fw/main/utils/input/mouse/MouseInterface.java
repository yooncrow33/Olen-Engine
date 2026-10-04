package com.fw.main.utils.input.mouse;

public interface MouseInterface {
    void mouseClicked(FwMouseAPI e);
    void mousePressed(FwMouseAPI e);
    void mouseReleased(FwMouseAPI e);
    void mouseEntered(FwMouseAPI e);
    void mouseExited(FwMouseAPI e);
    void mouseWheelMoved(FwMouseAPI e);

    /**
     * Invoked when a mouse button is pressed on a component and then dragged.
     */
    default void mouseDragged(FwMouseAPI e) {}

    /**
     * Invoked when the mouse cursor has been moved onto a component but no buttons have been pushed.
     */
    default void mouseMoved(FwMouseAPI e) {}
}