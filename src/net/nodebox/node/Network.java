/*
 * This file is part of NodeBox.
 *
 * Copyright (C) 2008 Frederik De Bleser (frederik@pandora.be)
 *
 * NodeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NodeBox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NodeBox. If not, see <http://www.gnu.org/licenses/>.
 */
package net.nodebox.node;

import javax.swing.event.EventListenerList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EventListener;
import java.util.HashMap;

public abstract class Network extends Node {

    /**
     * A list of all the nodes in this network.
     */
    private HashMap<String, Node> nodes = new HashMap<String, Node>();

    /**
     * The node being rendered in this network.
     */
    private Node renderedNode = null;

    /**
     * The event listeners registered to listen to this network.
     */
    private EventListenerList listeners = new EventListenerList();

    public static class NodeNotInNetwork extends RuntimeException {

        private Network network;
        private Node node;

        public NodeNotInNetwork(Network network, Node node) {
            this.network = network;
            this.node = node;
        }

        public Network getNetwork() {
            return network;
        }

        public Node getNode() {
            return node;
        }
    }

    //// Constructors ////

    public Network(Parameter.Type outputType) {
        super(outputType);
    }

    public Network(Parameter.Type outputType, String name) {
        super(outputType, name);
    }

    //// Container operations ////

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int size() {
        return nodes.size();
    }

    public void add(Node node) {
        assert (node != null);
        if (contains(node)) {
            return;
        }
        if (contains(node.getName())) {
            throw new Node.InvalidName(node, node.getName(), "There is already a node named \"" + node.getName() + "\" in this network.");
        }
        node._setNetwork(this);
        nodes.put(node.getName(), node);
        fireNodeAdded(node);
    }

    public Node create(Class nodeClass) {
        assert (nodeClass.isAssignableFrom(Node.class));
        try {
            Node newNode = (Node) nodeClass.newInstance();
            setUniqueNodeName(newNode);
            add(newNode);
            return newNode;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean remove(Node node) {
        assert (node != null);
        if (!contains(node)) {
            return false;
        }
        node.markDirty();
        // TODO: disconnect node from the old network.
        node.disconnect();
        nodes.remove(node.getName());
        if (node == renderedNode) {
            setRenderedNode(null);
        }
        fireNodeRemoved(node);
        return true;
    }

    public boolean contains(Node node) {
        return nodes.containsValue(node);
    }

    public boolean contains(String nodeName) {
        return nodes.containsKey(nodeName);
    }

    public Node getNode(String nodeName) {
        if (!contains(nodeName)) {
            throw new Node.NotFound(this, nodeName);
        }
        return nodes.get(nodeName);
    }

    public Collection<Node> getNodes() {
        return new ArrayList<Node>(nodes.values());
    }

    //// Naming operations ////

    /**
     * Changes the name of the given node to one that is unique within this network.
     * <p/>
     * Note that the node doesn't have to be in the network.
     *
     * @param node the node
     * @return the unique node name
     */
    public String setUniqueNodeName(Node node) {
        int counter = 1;
        while (true) {
            String suggestedName = node.defaultName() + counter;
            if (!contains(suggestedName)) {
                // We don't use rename here, since it assumes the node will be in 
                // this network.
                node.setName(suggestedName);
                return suggestedName;
            }
            ++counter;
        }
    }

    public boolean rename(Node node, String newName) {
        assert (contains(node));
        if (node.getName().equals(newName)) {
            return true;
        }
        if (contains(newName)) {
            return false;
        }
        nodes.remove(node.getName());
        node._setName(newName);
        nodes.put(newName, node);
        fireNodeChanged(node);
        return true;
    }

    //// Rendered node ////

    public Node getRenderedNode() {
        return renderedNode;
    }

    public void setRenderedNode(Node renderedNode) {
        if (renderedNode != null && !contains(renderedNode)) {
            throw new NodeNotInNetwork(this, renderedNode);
        }
        if (this.renderedNode == renderedNode) return;
        this.renderedNode = renderedNode;
        markDirty();
        fireRenderedNodeChanged(renderedNode);
    }

    //// Processing ////

    @Override
    public void markDirty() {
        super.markDirty();
        fireNetworkDirty();
    }

    @Override
    protected boolean process(ProcessingContext ctx) {
        if (renderedNode == null) {
            addError("No node to render");
            return false;
        }
        assert (contains(renderedNode));
        renderedNode.update();
        // TODO: place output of rendered node into network output.
        return true;
    }

    //// Cycle detection ////

    public boolean containsCycles() {
        return false;
    }

    //// Event handling ////

    public void addNetworkEventListener(NetworkEventListener l) {
        listeners.add(NetworkEventListener.class, l);
    }

    public void removeNetworkEventListener(NetworkEventListener l) {
        listeners.remove(NetworkEventListener.class, l);
    }

    public void fireNodeAdded(Node node) {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkEventListener.class))
            ((NetworkEventListener) l).nodeAdded(this, node);
    }

    public void fireNodeRemoved(Node node) {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkEventListener.class))
            ((NetworkEventListener) l).nodeRemoved(this, node);
    }

    public void fireConnectionAdded(Connection connection) {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkEventListener.class))
            ((NetworkEventListener) l).connectionAdded(this, connection);
    }

    public void fireConnectionRemoved(Connection connection) {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkEventListener.class))
            ((NetworkEventListener) l).connectionRemoved(this, connection);
    }

    public void fireRenderedNodeChanged(Node node) {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkEventListener.class))
            ((NetworkEventListener) l).renderedNodeChanged(this, node);
    }

    public void fireNodeChanged(Node node) {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkEventListener.class))
            ((NetworkEventListener) l).nodeChanged(this, node);
    }

    public void addNetworkDirtyListener(NetworkDirtyListener l) {
        listeners.add(NetworkDirtyListener.class, l);
    }

    public void removeNetworkDirtyListener(NetworkDirtyListener l) {
        listeners.remove(NetworkDirtyListener.class, l);
    }

    public void fireNetworkDirty() {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkDirtyListener.class))
            ((NetworkDirtyListener) l).networkDirty(this);
    }

}