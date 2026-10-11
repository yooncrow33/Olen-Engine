package com.fw.internal.sys.input;

import com.fw.main.Base;
import com.fw.main.utils.input.mouse.FwMouseAPI;
import com.fw.main.utils.input.mouse.MouseInterface;
import java.awt.event.*;

public class MouseAtBase implements MouseListener, MouseMotionListener, MouseWheelListener {

    private final Base base;
    private MouseInterface mouseInterface = null;

    public MouseAtBase(Base component) {
        this.base = component;
        component.addMouseListener(this);
        component.addMouseMotionListener(this);
        component.addMouseWheelListener(this);
    }

    public void registerInterface(MouseInterface mouseInterface) {
        if (this.mouseInterface != null) {
            System.err.println("MouseInterface is already registered!");
            return;
        }
        this.mouseInterface = mouseInterface;
    }

    private void handleMouseEvent(MouseEvent e, java.util.function.Consumer<FwMouseAPI> consumer) {
        if (!base.hasFocus()) {
            base.requestFocusInWindow();
        }

        if (this.mouseInterface == null) return;

        try {
            FwMouseAPI api = new FwMouseAPI();
            api.setRawEvent(e);
            consumer.accept(api);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        handleMouseEvent(e, api -> mouseInterface.mouseClicked(api));
    }

    @Override
    public void mousePressed(MouseEvent e) {
        handleMouseEvent(e, api -> mouseInterface.mousePressed(api));
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        handleMouseEvent(e, api -> mouseInterface.mouseReleased(api));
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        handleMouseEvent(e, api -> mouseInterface.mouseEntered(api));
    }

    @Override
    public void mouseExited(MouseEvent e) {
        handleMouseEvent(e, api -> mouseInterface.mouseExited(api));
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        handleMouseEvent(e, api -> mouseInterface.mouseDragged(api));
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        handleMouseEvent(e, api -> mouseInterface.mouseMoved(api));
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        handleMouseEvent(e, api -> mouseInterface.mouseWheelMoved(api));
    }
}