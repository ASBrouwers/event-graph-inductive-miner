package EventGraphs.Graphstream;

import java.util.*;
import java.util.stream.Collectors;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;

public class Sugiyama{
    Graph graph;
    List<List<Node>> layers;
    Map<String,Integer> nodeYPositions;
    List<Edge> allEdges;
    List<String> marked, stack;
    Integer edgeID;
    boolean removeCycles; //removeCycles = 1: Remove edges that cause cycles. removeCycles = 2: Change direction of edges that cause cycles

    public Sugiyama(Graph g, boolean removeCycles){
        graph = new MultiGraph("Copy");
        g.nodes().forEach(n -> graph.addNode(n.getId()));
        g.edges().forEach(e -> graph.addEdge(e.getId(),e.getSourceNode().getId(),e.getTargetNode().getId(),true));

        this.removeCycles = removeCycles;

        layers = new ArrayList<>();
        nodeYPositions = new HashMap<>();
        allEdges = graph.edges().collect(Collectors.toList());

        marked = new ArrayList<>();
        stack = new ArrayList<>();

        edgeID = -1;
    }

    public List<List<Node>> getLayers(){
        return layers;
    }

    public Map<String,Integer> getNodeYPositions(){
        return nodeYPositions;
    }

    public void applySugiyamaAlgorithm(){
        removeCycles();
        assignLayers();
        orderVertices();
        assignPositions();
    }

    private void removeCycles(){
        List<Node> nodes = graph.nodes().collect(Collectors.toList());

        for(Node n : nodes){
            dfsRemove(n);
        }
    }

    private void dfsRemove(Node node){
        if(marked.contains(node.getId())) return;

        marked.add(node.getId());
        stack.add(node.getId());

        List<Edge> outgoingEdges = node.leavingEdges().collect(Collectors.toList());
        for(Edge e : outgoingEdges){
            if(stack.contains(e.getTargetNode().getId())){
                if(removeCycles) allEdges.remove(e);
                else changeEdgeDirection(e);
            }else if(!marked.contains(e.getTargetNode().getId())){
                dfsRemove(e.getTargetNode());
            }
        }

        stack.remove(node.getId());
    }

    private void changeEdgeDirection(Edge e){
        Node middleNode = e.getSourceNode();
        Node targetNode = e.getTargetNode();
        String strEdgeID = String.valueOf(edgeID);
        Edge newEdge;

        //Add new edge from Target to Middle and remove the original edge from the list
        graph.addEdge(strEdgeID,targetNode,middleNode,true);
        newEdge = graph.getEdge(strEdgeID);
        allEdges.add(newEdge);
        allEdges.remove(e);

        edgeID--;
    }

    private void assignLayers(){
        List<Edge> edges = new ArrayList<>(allEdges);
        List<Node> nodes = graph.nodes().collect(Collectors.toList());

        List<Node> start = getVerticesWithoutIncomingEdges(edges, nodes);
        while(start.size() > 0){
            layers.add(start);
            List<Node> finalStart = start;
            edges = edges.stream().filter(e -> !finalStart.contains(e.getSourceNode())).collect(Collectors.toList());
            nodes.removeAll(start);
            start = getVerticesWithoutIncomingEdges(edges, nodes);
        }
    }

    private static List<Node> getVerticesWithoutIncomingEdges(List<Edge> edges, List<Node> nodes){
        List<Node> targets = edges.stream().map(Edge::getTargetNode).distinct().collect(Collectors.toList());

        return nodes.stream().filter(n -> !targets.contains(n)).collect(Collectors.toList());
    }

    private void orderVertices(){
        createVirtualVerticesAndEdges();

        for(int i = 0; i < 4; i++){ //Paper "A Technique for Drawing Directed Graphs" indicates i < 24
            median(i);
            transpose();
        }
    }

    private void assignPositions(){
        //After the layers have been ordered, medianPosition() is used to obtain the best Y coordinate for each node
        calculateInitialCoordinates();

        for(int i = 0; i < 2; i++){ //Paper "A Technique for Drawing Directed Graphs" indicates i < 8
            calculateCoordinates(i); // Look at previous/following layer for position reference
        }
    }

    private void createVirtualVerticesAndEdges(){
        int virtualIndex = 0;
        int virtualEdgeIndex = 0;

        for(int i = 0; i < layers.size()-1; i++){
            List<Node> currentLayer = layers.get(i);
            List<Node> nextLayer = layers.get(i+1);
            for(Node n : currentLayer){
                List<Edge> outgoingMulti = allEdges.stream()
                        .filter(e->e.getSourceNode() == n)
                        .filter(e-> Math.abs(getLayerNumber(e.getTargetNode())-getLayerNumber(n)) > 1)
                        .collect(Collectors.toList());
                List<Edge> incomingMulti = allEdges.stream()
                        .filter(e->e.getSourceNode() == n)
                        .filter(e-> Math.abs(getLayerNumber(e.getSourceNode())-getLayerNumber(n)) > 1)
                        .collect(Collectors.toList());
                for(Edge e : outgoingMulti){
                    graph.addNode("v_" + virtualIndex);
                    Node virtualNode = graph.getNode("v_" + virtualIndex);
                    nextLayer.add(virtualNode);
                    virtualIndex++;

                    allEdges.remove(e);
                    graph.addEdge("ve_"+virtualEdgeIndex,e.getSourceNode(),virtualNode,true);
                    allEdges.add(graph.getEdge("ve_"+virtualEdgeIndex));
                    virtualEdgeIndex++;
                    graph.addEdge("ve_"+virtualEdgeIndex,virtualNode,e.getTargetNode(),true);
                    allEdges.add(graph.getEdge("ve_"+virtualEdgeIndex));
                    virtualEdgeIndex++;
                }
                for(Edge e : incomingMulti){
                    graph.addNode("v_" + virtualIndex);
                    Node virtualNode = graph.getNode("v_" + virtualIndex);
                    nextLayer.add(virtualNode);
                    virtualIndex++;

                    allEdges.remove(e);
                    graph.addEdge("ve_"+virtualEdgeIndex,virtualNode,e.getTargetNode(),true);
                    allEdges.add(graph.getEdge("ve_"+virtualEdgeIndex));
                    virtualEdgeIndex++;
                    graph.addEdge("ve_"+virtualEdgeIndex,e.getSourceNode(),virtualNode,true);
                    allEdges.add(graph.getEdge("ve_"+virtualEdgeIndex));
                    virtualEdgeIndex++;
                }
            }
        }
    }

    private int getLayerNumber(Node node){
        int layerNum = -1;

        for(int i = 0; i < layers.size(); i++){
            List<Node> layer = layers.get(i);
            for(Node n : layer){
                if(n.getId().equals(node.getId())){
                    layerNum = i;
                    break;
                }
            }
            if(layerNum != -1) break;
        }

        return layerNum;
    }

    private void median(int i){
        if(i%2 == 0){
            for(int j = 1; j < layers.size(); j++){
                Map<String,Double> median = new HashMap<>();
                for(Node n : layers.get(j)){
                    median.put(n.getId(),getMedianValue(n,j-1, 1));
                }
                sortLayer(layers.get(j),median);
            }
        }else{
            for(int j = layers.size()-2; j >= 0; j--){
                Map<String,Double> median = new HashMap<>();
                for(Node n : layers.get(j)){
                    median.put(n.getId(),getMedianValue(n,j+1, 2));
                }
                sortLayer(layers.get(j),median);
            }
        }
    }

    private double getMedianValue(Node n, int layerNum, int direction){
        List<Integer> pos = adjPosition(n, layerNum, direction);
        int m = pos.size()/2;

        if(pos.size() == 0) return -1.0;
        else if(pos.size()%2 == 1) return pos.get(m);
        else if(pos.size() == 2) return (pos.get(0) + pos.get(1))/2.0;
        else{
            int left = pos.get(m-1) - pos.get(0);
            int right = pos.get(pos.size()-1) - pos.get(m);
            return (pos.get(m-1)*right + pos.get(m)*left)/(left+right);
        }
    }

    private List<Integer> adjPosition(Node node, int layerNum, int direction){
        List<Integer> adjPos = new ArrayList<>();
        int pos = 0;
        for(Node n : layers.get(layerNum)){
            long adjEdges;
            if(direction == 1){
                adjEdges = allEdges.stream()
                        .filter(e -> e.getSourceNode().getId().equals(n.getId()) && e.getTargetNode().getId().equals(node.getId()))
                        .count();
            }else{
                adjEdges = allEdges.stream()
                        .filter(e -> e.getSourceNode().getId().equals(node.getId()) && e.getTargetNode().getId().equals(n.getId()))
                        .count();
            }
            if(adjEdges > 0) adjPos.add(pos);
            pos++;
        }

        return adjPos;
    }

    private void sortLayer(List<Node> layer, Map<String, Double> median){
        boolean swapped;
        for(int i = 0; i < layer.size()-1; i++){
            swapped = false;
            for(int j = 0; j < layer.size()-i-1; j++){
                Double m1 = median.get(layer.get(j).getId());
                Double m2 = median.get(layer.get(j+1).getId());
                if(m1 > m2){
                    Collections.swap(layer,j,j+1);
                    swapped = true;
                }
            }
            if(!swapped) break;
        }
    }

    private void transpose(){
        boolean improved = true;
        while(improved){
            improved = false;
            for(int i = 0; i < layers.size()-1; i++){
                for(int j = 0; j < layers.get(i).size()-2; j++){
                    Node v = layers.get(i).get(j);
                    Node w = layers.get(i).get(j+1);
                    if(swapImproves(i+1,v,w)){
                        improved = true;
                        Collections.swap(layers.get(i),j,j+1);
                    }
                }
            }
        }
    }

    private boolean swapImproves(int nextLayerIndex, Node v, Node w){
        //Get position of nodes connected to nodes v and w in adjacent layer
        int i = 0; //i indicates the position of the nodes
        List<Integer> aEdges = new ArrayList<>();
        List<Integer> bEdges = new ArrayList<>();
        for(Node n : layers.get(nextLayerIndex)){
            long existingEdge = allEdges.stream().filter(e -> e.getSourceNode().getId().equals(v.getId()) && e.getTargetNode().getId().equals(n.getId())).count();
            if(existingEdge > 0) aEdges.add(i);

            existingEdge = allEdges.stream().filter(e -> e.getSourceNode().getId().equals(w.getId()) && e.getTargetNode().getId().equals(n.getId())).count();
            if(existingEdge > 0) bEdges.add(i);

            i++;
        }

        int numCrossingsOriginal = 0;
        int numCrossingsSwapped = 0;
        for(int a : aEdges){
            for(int b : bEdges){
                if(a > b) numCrossingsOriginal++; //In the original order, having the index of a > b represents a line crossing
                if(a < b) numCrossingsSwapped++; //In the swapped order, b < a represents a line crossing
            }
        }

        return numCrossingsOriginal > numCrossingsSwapped; //If number of crossings decreases with swap, then the swap improves the layout
    }

    private void calculateInitialCoordinates(){
        int yCoord = 0;
        // Get initial Y position for all nodes in every layer. Initial position is the same as their placement in the list
        for (List<Node> layer : layers) {
            for (Node n : layer) {
                nodeYPositions.put(n.getId(), yCoord);
                yCoord--;
            }
            yCoord = 0;
        }
    }

    private void calculateCoordinates(int dir){
        if(dir%2 == 0) {
            for (int j = 1; j < layers.size(); j++) {
                List<Integer> layerPositions = new ArrayList<>();
                for (Node n : layers.get(j)) {
                    // Calculate median position for the node based on its adjacent nodes on the left.
                    // Example: If the node is connected to 3 nodes in positions 4,8,10 (respectively), the added value will be 8.
                    layerPositions.add(getMedianPos(n, j - 1, 0));
                }
                calculateLayerCoordinates(j,layerPositions);
            }
        }else{
            for(int j = layers.size()-2; j >= 0; j--){
                List<Integer> layerPositions = new ArrayList<>();
                for (Node n : layers.get(j)) {
                    // Calculate median position for the node based on its adjacent nodes on the right.
                    layerPositions.add(getMedianPos(n, j + 1, 1));
                }
                calculateLayerCoordinates(j,layerPositions);
            }
        }
    }

    private void calculateLayerCoordinates(int layerNum, List<Integer> layerPositions){
        int minPos = 0; // Minimum coordinate where the node can be placed. Nodes cannot be placed higher than coordinate 0.

        for (int i = 0; i < layers.get(layerNum).size(); i++) {
            String nodeId = layers.get(layerNum).get(i).getId(); // Get node id so its placement coordinate can be referenced later
            int nodePos = layerPositions.get(i); // Node position according to the calculated median
            int nextPos = 1; // Variable indicating coordinate of the next node in the layer
            int nextPosIdx = i + 1; // Index to retrieve coordinate from layerPositions list
            int samePosCount = 1; // Variable used to count how many nodes in the layer share the same median value
            if (nextPosIdx != layerPositions.size())
                nextPos = layerPositions.get(nextPosIdx); // Get actual coordinate of next node in layer
            while (nodePos == nextPos) {
                // If the next node has the same coordinate (based on median values) as the current one, their position might be calculated differently
                // to center nodes as much as possible. For example, if nodes A, B, and C are only connected to node D (y = -2), the median for the 3 nodes
                // will be -2. However, this check allows us to place them around D, with A (y = -1), B (y = -2), and C (y = -3) having updated y coordinates
                // around y = -2.
                samePosCount++;
                nextPosIdx++;
                if (nextPosIdx == layerPositions.size()) break;
                nextPos = layerPositions.get(nextPosIdx);
            }

            if (samePosCount == 1) {
                // If this is the only node that should be placed on the given coordinate, we only need to validate against the minPos, to make sure
                // the node is placed in an empty coordinate and not overlap another node.
                if (nodePos < minPos) minPos = nodePos;
                nodeYPositions.put(nodeId, minPos);
                minPos--; // Once the new position has been updated in the map, the minPos must be updated.
            } else {
                // When more than one node shares the same coordinate, they must be spread to make sure they are placed around the connecting node.
                // The offset is applied to the first node so it is placed higher than the median. For example:
                // If A is connected to 3 nodes, the offset is 3/2 = 1.5 = 1. So the first node will be placed one spot above A, so the second can be
                // placed at the same as A and the third can be placed one spot below A, centering the graph.
                // If A were connected to 4 nodes, the offset will be reduced by 1, so the center is closer to the highest node.
                int offset = samePosCount / 2;
                if (samePosCount % 2 == 0) offset--;

                // The offset will only apply if there is still enough space above, which can be verified by comparing the coordinate against the
                // minPos variable.
                if ((nodePos + offset) < minPos) minPos = nodePos + offset;
                nodeYPositions.put(nodeId, minPos);
                minPos--;

                // After placing the first node, the subsequent nodes are placed one spot below as explained previously.
                int count = 1;
                while (count != samePosCount) {
                    nodeId = layers.get(layerNum).get(i + count).getId();
                    nodeYPositions.put(nodeId, minPos);
                    minPos--;
                    count++;
                }
                // Given that subsequent nodes in the layer have already been arranged, they can be skipped from the for loop.
                i += samePosCount - 1;
            }
        }
    }

    private int getMedianPos(Node n, int layerNum, int direction){
        List<Integer> pos = adjPositionCoordinates(n, layerNum, direction);
        int m = pos.size()/2;

        if(pos.size() == 0) return 1;
        else if(pos.size()%2 == 1) return pos.get(m);
        else if(pos.size() == 2) return (pos.get(0) + pos.get(1))/2;
        else{
            int left = pos.get(m-1) - pos.get(0);
            int right = pos.get(pos.size()-1) - pos.get(m);
            return (pos.get(m-1)*right + pos.get(m)*left)/(left+right);
        }
    }

    private List<Integer> adjPositionCoordinates(Node node, int layerNum, int direction){
        List<Integer> adjPos = new ArrayList<>();

        for(Node n : layers.get(layerNum)){
            long adjEdges;
            if(direction == 0){
                adjEdges = allEdges.stream()
                        .filter(e -> e.getSourceNode().getId().equals(n.getId()) && e.getTargetNode().getId().equals(node.getId()))
                        .count();
            }else{
                adjEdges = allEdges.stream()
                        .filter(e -> e.getSourceNode().getId().equals(node.getId()) && e.getTargetNode().getId().equals(n.getId()))
                        .count();
            }
            if(adjEdges > 0) adjPos.add(nodeYPositions.get(n.getId()));
        }

        return adjPos;
    }
}