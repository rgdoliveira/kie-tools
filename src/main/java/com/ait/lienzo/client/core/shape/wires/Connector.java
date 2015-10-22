/*
   Copyright (c) 2014,2015 Ahome' Innovation Technologies. All rights reserved.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
// TODO - review DSJ

package com.ait.lienzo.client.core.shape.wires;

import static com.ait.lienzo.client.core.shape.wires.IControlHandle.ControlHandleStandardType.POINT;

import com.ait.lienzo.client.core.Context2D;
import com.ait.lienzo.client.core.event.NodeDragEndEvent;
import com.ait.lienzo.client.core.event.NodeDragEndHandler;
import com.ait.lienzo.client.core.event.NodeMouseClickEvent;
import com.ait.lienzo.client.core.event.NodeMouseClickHandler;
import com.ait.lienzo.client.core.event.NodeMouseDoubleClickEvent;
import com.ait.lienzo.client.core.event.NodeMouseDoubleClickHandler;
import com.ait.lienzo.client.core.event.NodeMouseEnterEvent;
import com.ait.lienzo.client.core.event.NodeMouseEnterHandler;
import com.ait.lienzo.client.core.event.NodeMouseExitEvent;
import com.ait.lienzo.client.core.event.NodeMouseExitHandler;
import com.ait.lienzo.client.core.shape.AbstractDirectionalMultiPointShape;
import com.ait.lienzo.client.core.shape.AbstractMultiPointShape;
import com.ait.lienzo.client.core.shape.IPrimitive;
import com.ait.lienzo.client.core.shape.Node;
import com.ait.lienzo.client.core.shape.Shape;
import com.ait.lienzo.client.core.types.BoundingBox;
import com.ait.lienzo.client.core.types.ImageData;
import com.ait.lienzo.client.core.types.PathPartEntryJSO;
import com.ait.lienzo.client.core.types.PathPartList;
import com.ait.lienzo.client.core.types.Point2D;
import com.ait.lienzo.client.core.types.Point2DArray;
import com.ait.lienzo.client.core.util.ScratchPad;
import com.ait.lienzo.client.widget.DragConstraintEnforcer;
import com.ait.lienzo.client.widget.DragContext;
import com.ait.lienzo.shared.core.types.ArrowEnd;
import com.ait.tooling.nativetools.client.collection.NFastDoubleArrayJSO;
import com.ait.tooling.nativetools.client.collection.NFastStringMap;
import com.ait.tooling.nativetools.client.event.HandlerRegistrationManager;
import com.ait.tooling.nativetools.client.util.Console;
import com.google.gwt.user.client.Timer;

public class Connector
{
    private Connection                 m_headConnection;

    private Connection                 m_tailConnection;

    private IControlHandleList         m_pointHandles;

    private HandlerRegistrationManager m_HandlerRegistrationManager;

    private AbstractMultiPointShape<?> m_line;

    private WiresManager               m_manager;

    public Connector(Magnet headMagnet, Magnet tailMagnet, AbstractDirectionalMultiPointShape<?> line, WiresManager manager)
    {
        m_line = line;

        m_manager = manager;

        setHeadConnection(new Connection(this, line, ArrowEnd.HEAD));
        m_headConnection.setMagnet(headMagnet);
        setTailConnection(new Connection(this, line, ArrowEnd.TAIL));
        m_tailConnection.setMagnet(tailMagnet);

        m_HandlerRegistrationManager = new HandlerRegistrationManager();

        ConnectorHandler handler = new ConnectorHandler(this);
        m_HandlerRegistrationManager.register(line.addNodeMouseEnterHandler(handler));
        m_HandlerRegistrationManager.register(line.addNodeMouseExitHandler(handler));
        m_HandlerRegistrationManager.register(line.addNodeMouseClickHandler(handler));

        // The Line is only draggable if both Connections are unconnected
        setDraggable();
    }

    public WiresManager getWiresManager()
    {
        return m_manager;
    }

    public static class ConnectionHandler implements NodeDragEndHandler, DragConstraintEnforcer
    {
        private Connector m_connector;

        private boolean   m_head;

        private ImageData m_shapesBacking;

        private ImageData m_magnetsBacking;

        private IMagnets  m_magnets;

        private double    m_startX;

        private double    m_startY;

        private final NFastStringMap<WiresShape> m_shape_color_map  = new NFastStringMap<WiresShape>();

        private final NFastStringMap<Magnet>        m_magnet_color_map = new NFastStringMap<Magnet>();

        public ConnectionHandler(Connector connector)
        {
            m_connector = connector;
        }

        @Override
        public void onNodeDragEnd(NodeDragEndEvent event)
        {
            if (m_magnets != null)
            {
                m_magnets.hide();
            }

            m_shapesBacking = null;// uses lots of memory, so let it GC
            m_magnetsBacking = null;// uses lots of memory, so let it GC
            m_magnets = null;// if this is not nulled, the Mangets reference could stop Magnets being GC, when not used anywhere else
            m_shape_color_map.clear();
            m_magnet_color_map.clear();
        }

        @Override
        public void startDrag(DragContext dragContext)
        {
            Node<?> node = dragContext.getNode().asNode();

            m_head = node == m_connector.getHeadConnection().getControl();

            Point2D points = node.getAbsoluteLocation();
            m_startX = points.getX();
            m_startY = points.getY();

            ScratchPad scratch = m_connector.m_line.getLayer().getScratchPad();

            Connection c = getConnection();
            WiresLayer layer = c.getConnector().getWiresManager().getLayer();

            m_shapesBacking = MagnetManager.drawShapesToBacking(layer.getChildShapes(), scratch, null, m_shape_color_map);
            //m_connector.getLine().getOverLayer().getContext().dr(m_shapesBacking, 100, 100);
            m_connector.getLine().getOverLayer().getContext().createImageData(m_shapesBacking);



            if (c.getMagnet() != null)
            {
                m_magnets = c.getMagnet().getMagnets();
                m_magnetsBacking = MagnetManager.drawMagnetsToBack(m_magnets, m_shape_color_map, m_magnet_color_map, scratch);
            }

            String colorKey = MagnetManager.findColorAtPoint(m_shapesBacking, (int) m_startX, (int) m_startY);
            showMagnets((int) m_startX, (int) m_startY, colorKey);
        }

        private String m_colorKey;

        @Override
        public boolean adjust(Point2D dxy)
        {
            Connection c = getConnection();
            Shape<?> control = null;
            Magnet magnet = null;
            int x = (int) (m_startX + dxy.getX());
            int y = (int) (m_startY + dxy.getY());

            String colorKey = MagnetManager.findColorAtPoint(m_shapesBacking, x, y);
            if ( m_colorKey != null && !colorKey.equals(m_colorKey) )
            {
                // this can happen when the mouse moves from an outer shape to an inner shape, or vice-sersa
                // hide and null, and it'll show for the new.
                m_magnets.hide();
                m_magnets = null;
                m_colorKey = null;
            }


            if (m_magnets == null)
            {
                showMagnets(x, y, colorKey);
            }

            if (m_magnets != null)
            {
                String magnetColorKey = MagnetManager.findColorAtPoint(m_magnetsBacking, x, y);
                if (magnetColorKey == null)
                {
                    m_magnets.hide();
                    m_magnets = null;
                    m_colorKey = null;
                    magnet = null;
                }
                else
                {

                    magnet = m_magnet_color_map.get(magnetColorKey);
                    if (magnet != null) // it can be null, when over the main shape, instead of a magnet
                    {
                        control = magnet.getControl().asShape();
                    }
                }
            }

            if (magnet != c.getMagnet())
            {
                c.setMagnet(magnet);
            }

            if (control != null)
            {
                // If there is a control, snap to it
                Point2D absControl = control.getAbsoluteLocation();
                double targetX = absControl.getX();
                double targetY = absControl.getY();

                double dx = targetX - m_startX - dxy.getX();
                double dy = targetY - m_startY - dxy.getY();

                if (dx != 0 || dy != 0)
                {
                    dxy.setX(dxy.getX() + dx).setY(dxy.getY() + dy);
                    return true;
                }
            }

            return false;
        }

        private void showMagnets(int x, int y, String colorKey)
        {
            ScratchPad scratch = m_connector.m_line.getLayer().getScratchPad();

            if (colorKey != null)
            {
                WiresShape prim = m_shape_color_map.get(colorKey);
                if (prim != null)
                {
                    m_magnets = prim.getMagnets();
                    m_colorKey = colorKey;
                    if (m_magnets != null)
                    {
                        m_magnets.show();
                        m_magnetsBacking = MagnetManager.drawMagnetsToBack(m_magnets, m_shape_color_map, m_magnet_color_map, scratch);
                    }
                    else
                    {
                        // added this defensive check here, just in case - it should never be triggered.
                        throw new IllegalStateException("It should not be possible to find a shape, that does not have magnets, please report. (Defensive Programming)");
                    }
                }
            }
        }

        public Connection getConnection()
        {
            if (m_head)
            {
                return m_connector.getHeadConnection();
            }
            else
            {
                return m_connector.getTailConnection();
            }
        }
    }

    public static class ConnectorHandler implements NodeMouseExitHandler, NodeMouseEnterHandler, NodeMouseClickHandler, NodeMouseDoubleClickHandler
    {
        private Connector                  m_connector;

        private HandlerRegistrationManager m_HandlerRegistrationManager;

        private Timer                      m_timer;

        public ConnectorHandler(Connector connector)
        {
            m_connector = connector;
        }

        @Override
        public void onNodeMouseClick(NodeMouseClickEvent event)
        {
            if (event.isShiftKeyDown())
            {
                m_connector.destroyPointHandles();
                Point2DArray oldPoints = m_connector.getLine().getPoint2DArray();

                int pointIndex = getIndexForSelectedSegment(event, oldPoints);
                if (pointIndex > 0)
                {
                    Point2D point = new Point2D(event.getX(), event.getY());
                    Point2DArray newPoints = new Point2DArray();
                    newPoints.push(oldPoints.get(0));
                    for (int i = 1; i < pointIndex; i++)
                    {
                        newPoints.push(oldPoints.get(i));
                    }
                    newPoints.push(point);
                    for (int i = pointIndex; i < oldPoints.size(); i++)
                    {
                        newPoints.push(oldPoints.get(i));
                    }
                    m_connector.getLine().setPoint2DArray(newPoints);

                    showPointHandles();
                }
            }
        }

        @Override
        public void onNodeMouseDoubleClick(NodeMouseDoubleClickEvent event)
        {
            Object control = event.getSource();
            IControlHandle selected = null;
            for (IControlHandle handle : m_connector.getPointHandles())
            {
                if (handle.getControl() == control)
                {
                    selected = handle;
                    break;
                }
            }

            Point2DArray oldPoints = m_connector.getLine().getPoint2DArray();
            Point2DArray newPoints = new Point2DArray();
            Point2D selectedPoint2D = selected.getControl().getLocation();
            for (int i = 0; i < oldPoints.size(); i++)
            {
                Point2D current = oldPoints.get(i);
                if (!current.equals(selectedPoint2D))
                {
                    newPoints.push(current);
                }
            }

            m_connector.destroyPointHandles();
            m_connector.getLine().setPoint2DArray(newPoints);
            showPointHandles();
        }

        @Override
        public void onNodeMouseEnter(NodeMouseEnterEvent event)
        {
            if (m_timer != null)
            {
                m_timer.cancel();
                m_timer = null;
            }

            if (event.getSource() == m_connector.getLine() && m_HandlerRegistrationManager == null && event.isShiftKeyDown())
            {
                showPointHandles();
            }
        }

        @Override
        public void onNodeMouseExit(NodeMouseExitEvent event)
        {
            if (m_HandlerRegistrationManager != null)
            {
                createHideTimer();
            }
        }

        private int getIndexForSelectedSegment(NodeMouseClickEvent event, Point2DArray oldPoints)
        {
            NFastStringMap<Integer> colorMap = new NFastStringMap<Integer>();

            AbstractMultiPointShape<?> line = m_connector.getLine();
            ScratchPad scratch = line.getScratchPad();
            scratch.clear();
            PathPartList path = line.getPathPartList();
            int pointsIndex = 0;
            String color = MagnetManager.m_c_rotor.next();
            colorMap.put(color, pointsIndex);
            Context2D ctx = scratch.getContext();
            double strokeWidth = line.getStrokeWidth();
            ctx.setStrokeWidth(strokeWidth);

            Point2D absolutePos = line.getAbsoluteLocation();
            double offsetX = absolutePos.getX();
            double offsetY = absolutePos.getY();

            Point2D pathStart = new Point2D(offsetX, offsetY);
            Point2D segmentStart = pathStart;

            for (int i = 0; i < path.size(); i++)
            {
                PathPartEntryJSO entry = path.get(i);
                NFastDoubleArrayJSO points = entry.getPoints();

                switch (entry.getCommand())
                {
                    case PathPartEntryJSO.MOVETO_ABSOLUTE:
                    {
                        double x0 = points.get(0) + offsetX;
                        double y0 = points.get(1) + offsetY;
                        Point2D m = new Point2D(x0, y0);
                        if (i == 0)
                        {
                            // this is position is needed, if we close the path.
                            pathStart = m;
                        }
                        segmentStart = m;
                        break;
                    }
                    case PathPartEntryJSO.LINETO_ABSOLUTE:
                    {
                        points = entry.getPoints();
                        double x0 = points.get(0) + offsetX;
                        double y0 = points.get(1) + offsetY;
                        Point2D end = new Point2D(x0, y0);

                        if (oldPoints.get(pointsIndex).equals(segmentStart))
                        {
                            pointsIndex++;
                            color = MagnetManager.m_c_rotor.next();
                            colorMap.put(color, pointsIndex);
                        }
                        ctx.setStrokeColor(color);

                        ctx.beginPath();
                        ctx.moveTo(segmentStart.getX(), segmentStart.getY());
                        ctx.lineTo(x0, y0);
                        ;
                        ctx.stroke();
                        segmentStart = end;
                        break;
                    }
                    case PathPartEntryJSO.CLOSE_PATH_PART:
                    {
                        double x0 = pathStart.getX() + offsetX;
                        double y0 = pathStart.getY() + offsetY;
                        Point2D end = new Point2D(x0, y0);
                        if (oldPoints.get(pointsIndex).equals(segmentStart))
                        {
                            pointsIndex++;
                            color = MagnetManager.m_c_rotor.next();
                            colorMap.put(color, pointsIndex);
                        }
                        ctx.setStrokeColor(color);
                        ctx.beginPath();
                        ctx.moveTo(segmentStart.getX(), segmentStart.getY());
                        ctx.lineTo(x0, y0);
                        ctx.stroke();
                        segmentStart = end;
                        break;
                    }
                    case PathPartEntryJSO.CANVAS_ARCTO_ABSOLUTE:
                    {
                        points = entry.getPoints();

                        double x0 = points.get(0) + offsetX;
                        double y0 = points.get(1) + offsetY;
                        Point2D p0 = new Point2D(x0, y0);

                        double x1 = points.get(2) + offsetX;
                        double y1 = points.get(3) + offsetY;
                        double r = points.get(4);
                        Point2D p1 = new Point2D(x1, y1);
                        Point2D end = p1;

                        if (p0.equals(oldPoints.get(pointsIndex)))
                        {
                            pointsIndex++;
                            color = MagnetManager.m_c_rotor.next();
                            colorMap.put(color, pointsIndex);
                        }
                        ctx.setStrokeColor(color);
                        ctx.beginPath();
                        ctx.moveTo(segmentStart.getX(), segmentStart.getY());
                        ctx.arcTo(x0, y0, x1, y1, r);
                        ctx.stroke();

                        segmentStart = end;
                    }
                        break;
                }
            }

            BoundingBox box = line.getBoundingBox();

            int mouseX = event.getX();
            int mouseY = event.getY();

            // Keep the ImageData small by clipping just the visible line area
            // But remember the mouse must be offset for this clipped area.
            int sx = (int) (box.getX() - strokeWidth - offsetX);
            int sy = (int) (box.getY() - strokeWidth - offsetY);

            ImageData backing = ctx.getImageData(sx, sy, (int) (box.getWidth() + strokeWidth + strokeWidth), (int) (box.getHeight() + strokeWidth + strokeWidth));

            color = MagnetManager.findColorAtPoint(backing, mouseX - sx, mouseY - sy);
            pointsIndex = colorMap.get(color);
            return pointsIndex;
        }

        private void showPointHandles()
        {
            if (m_HandlerRegistrationManager == null)
            {
                m_HandlerRegistrationManager = m_connector.getPointHandles().getHandlerRegistrationManager();
            }
            m_connector.getPointHandles().show(m_connector.getLine().getLayer());

            ConnectionHandler connectionHandler = new ConnectionHandler(m_connector);

            Shape<?> head = m_connector.getHeadConnection().getControl().asShape();
            head.setDragConstraints(connectionHandler);
            m_HandlerRegistrationManager.register(head.addNodeDragEndHandler(connectionHandler));

            Shape<?> tail = m_connector.getTailConnection().getControl().asShape();
            tail.setDragConstraints(connectionHandler);
            m_HandlerRegistrationManager.register(tail.addNodeDragEndHandler(connectionHandler));

            for (IControlHandle handle : m_connector.m_pointHandles)
            {
                Shape<?> shape = handle.getControl().asShape();
                m_HandlerRegistrationManager.register(shape.addNodeMouseEnterHandler(this));
                m_HandlerRegistrationManager.register(shape.addNodeMouseExitHandler(this));
                m_HandlerRegistrationManager.register(shape.addNodeMouseDoubleClickHandler(this));

            }
        }

        public void createHideTimer()
        {
            if (m_timer == null)
            {
                m_timer = new Timer()
                {
                    @Override
                    public void run()
                    {
                        if (m_HandlerRegistrationManager != null)
                        {
                            m_HandlerRegistrationManager.destroy();
                        }
                        m_HandlerRegistrationManager = null;
                        m_connector.getPointHandles().hide();
                    }
                };
                m_timer.schedule(1000);
            }
        }
    }

    public Connection getHeadConnection()
    {
        return m_headConnection;
    }

    public void setHeadConnection(Connection headConnection)
    {
        m_headConnection = headConnection;

    }

    public void setDraggable()
    {
        // The line can only be dragged if both Magnets are null
        m_line.setDraggable(getHeadConnection().getMagnet() == null && getTailConnection().getMagnet() == null);
    }

    public Connection getTailConnection()
    {
        return m_tailConnection;
    }

    public void setTailConnection(Connection tailConnection)
    {
        m_tailConnection = tailConnection;
    }

    public void setPointHandles(IControlHandleList pointHandles)
    {
        m_pointHandles = pointHandles;
    }

    public AbstractMultiPointShape<?> getLine()
    {
        return m_line;
    }

    public void setLine(AbstractMultiPointShape<?> line)
    {
        m_line = line;
    }

    public void destroyPointHandles()
    {
        m_pointHandles.destroy();
        m_pointHandles = null;
    }

    public IControlHandleList getPointHandles()
    {
        if (m_pointHandles == null)
        {
            m_pointHandles = (IControlHandleList) m_line.getControlHandles(POINT).get(POINT);
        }
        return m_pointHandles;
    }
}
