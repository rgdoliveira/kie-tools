package com.ait.lienzo.client.core.shape.wires;

import java.util.Collection;

import com.ait.lienzo.client.core.shape.AbstractDirectionalMultiPointShape;
import com.ait.lienzo.client.core.shape.Group;
import com.ait.lienzo.client.core.shape.Layer;
import com.ait.lienzo.client.core.shape.MultiPath;
import com.ait.lienzo.client.core.util.Geometry;
import com.ait.lienzo.shared.core.types.EventPropagationMode;
import com.ait.tooling.nativetools.client.collection.NFastArrayList;
import com.ait.tooling.nativetools.client.collection.NFastStringMap;
import com.ait.lienzo.client.core.shape.wires.DragAndDropManager.WiresShapeDragHandler;

public class WiresManager
{
    private static final WiresManager       m_instance           = new WiresManager();

    public static final WiresManager getInstance()
    {
        return m_instance;
    }

    private final        MagnetManager      m_magnetManager      = MagnetManager.getInstance();

    private final        DragAndDropManager m_dragAndDropManager = DragAndDropManager.getInstance();

    private AlignAndDistribute m_index;

    private NFastStringMap<WiresShape> m_shapesMap = new NFastStringMap<WiresShape>();

    private NFastArrayList<WiresShape> m_shapesList = new NFastArrayList<WiresShape>();

    private WiresLayer m_layer;



    public WiresManager()
    {

    }

    public void init(Layer layer)
    {
        m_layer = new WiresLayer(layer);
        m_index = new AlignAndDistribute(layer);
    }

    public WiresShape createShape(MultiPath path)
    {
        path.setDraggable(false);
        Group group = new Group();
        group.add(path);
        group.setDraggable(true);
        group.setEventPropagationMode(EventPropagationMode.FIRST_ANCESTOR);
        WiresShape shape = new WiresShape(path, group, this);
        m_shapesMap.put(shape.getGroup().uuid(), shape);

        WiresShapeDragHandler handler = new WiresShapeDragHandler(DragAndDropManager.getInstance(), shape, this);
        group.addNodeMouseDownHandler(handler);
        group.addNodeMouseUpHandler(handler);
        group.addNodeDragStartHandler(handler);
        group.addNodeDragMoveHandler(handler);
        group.addNodeDragEndHandler(handler);

        return shape;
    }

    public Connector createConnector(Magnet headMagnet, Magnet tailMagnet, AbstractDirectionalMultiPointShape<?> line)
    {
        return new Connector(headMagnet, tailMagnet, line, this);
    }

    public WiresLayer getLayer()
    {
        return m_layer;
    }

    public WiresShape getShape(String uuid)
    {
        return m_shapesMap.get(uuid);
    }

    public NFastArrayList<WiresShape>  getShapes()
    {
        return m_shapesList;
    }

    public void addToIndex(WiresShape shape)
    {
        m_index.addShape(shape.getGroup());
    }

    public void removeFromIndex(WiresShape shape)
    {
        m_index.removeShape(shape.getGroup());
    }

    public void createMagnets(WiresShape shape)
    {
        IMagnets magnets = m_magnetManager.createMagnets(shape.getPath(), shape.getGroup(), Geometry.getCardinalIntersects(shape.getPath()), shape);
        shape.setMagnets(magnets);
    }
}
