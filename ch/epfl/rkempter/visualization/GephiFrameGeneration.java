/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.epfl.rkempter.visualization;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import javax.imageio.ImageIO;
import org.gephi.data.attributes.api.AttributeColumn;
import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.data.attributes.api.AttributeTable;
import org.gephi.data.attributes.api.AttributeType;
import org.gephi.datalab.api.AttributeColumnsMergeStrategiesController;
import org.gephi.dynamic.api.DynamicController;
import org.gephi.dynamic.api.DynamicModel;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.DirectedGraph;
import org.gephi.io.database.drivers.MySQLDriver;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.EdgeDefault;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.importer.plugin.database.EdgeListDatabaseImpl;
import org.gephi.io.importer.plugin.database.ImporterEdgeList;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.datalab.api.AttributeColumnsController;
import org.gephi.dynamic.api.DynamicGraph;
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.filters.api.Range;
import org.gephi.filters.plugin.dynamic.DynamicRangeBuilder;
import org.gephi.filters.plugin.dynamic.DynamicRangeBuilder.DynamicRangeFilter;
import org.gephi.filters.plugin.graph.DegreeRangeBuilder;
import org.gephi.filters.plugin.operator.INTERSECTIONBuilder;
import org.gephi.filters.spi.FilterBuilder;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.EdgeIterable;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphView;
import org.gephi.graph.api.Node;
import org.gephi.io.exporter.preview.PNGExporter;
import org.gephi.io.exporter.spi.GraphExporter;
import org.gephi.layout.api.LayoutController;
import org.gephi.layout.plugin.circularlayout.radialaxislayout.RadialAxisLayout;
import org.gephi.plugins.layout.noverlap.NoverlapLayout;
import org.gephi.partition.api.Partition;
import org.gephi.partition.api.PartitionController;
import org.gephi.partition.plugin.EdgeColorTransformer;
import org.gephi.preview.PreviewModelImpl;
import org.gephi.preview.types.EdgeColor.Mode;
import org.gephi.preview.types.EdgeColor;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperties;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.api.ProcessingTarget;
import org.gephi.preview.api.RenderTarget;
import org.gephi.preview.types.DependantOriginalColor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.ranking.api.Ranking;
import org.gephi.ranking.api.RankingController;
import org.gephi.ranking.api.Transformer;
import org.gephi.ranking.plugin.transformer.AbstractSizeTransformer;
import org.gephi.utils.longtask.spi.LongTask;
import org.gephi.utils.progress.Progress;
import org.gephi.utils.progress.ProgressTicket;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import processing.core.PGraphicsJava2D;

/**
 *
 * @author Renato Kempter
 */
public class GephiFrameGeneration extends Thread {
    
    private static final int FRAMES_PER_SECOND = 2;
    private static final int IMAGE_WIDTH = 1600;
    private static final int IMAGE_HEIGHT = 1200;
    
    private String startDateTime = null;
    private String endDateTime = null;
    private File folder = null;
    private int length;
    private int start;
    
    public GephiFrameGeneration(String start, String end, int length, int startTime) {
        startDateTime = start;
        endDateTime = end;
        this.length = length;
        this.start = startTime;
        System.out.println("Start is "+startTime);
    }
    
    @Override
    public void run() {
        createImages();
        createMovie();
    }
    
    public void createImages() {
        //Init a project - and therefore a workspace
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        Workspace workspace = pc.getCurrentWorkspace();

        //Get controllers and models
        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        DynamicController dynamicController = Lookup.getDefault().lookup(DynamicController.class);
        DynamicModel dynamicModel = dynamicController.getModel();
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
        AttributeModel attributeModel = Lookup.getDefault().lookup(AttributeController.class).getModel();
        AttributeColumnsController acc = Lookup.getDefault().lookup(AttributeColumnsController.class);
        AttributeColumnsMergeStrategiesController merger = Lookup.getDefault().lookup(AttributeColumnsMergeStrategiesController.class);
        
        //Import database
        EdgeListDatabaseImpl db = new EdgeListDatabaseImpl();
        db.setDBName("24h");
        db.setHost("localhost");
        db.setUsername("root");
        db.setPasswd("");
        db.setSQLDriver(new MySQLDriver());
        db.setPort(3306);
        
        String nodeQuery = String.format(
                "SELECT DISTINCT \n" +
                "   news.id AS id, \n"+
                "   news.title AS label \n"+
                "FROM news, edges \n"+
                "WHERE "+
                "   (news.id = edges.source OR \n"+
                "   news.id = edges.target) AND \n"+
                "   edges.date > '%s' AND\n"+
                "   edges.date < '%s' ", startDateTime, endDateTime);
//        
        String edgeQuery = String.format(
                "SELECT\n" +
                "	edges.source AS source,\n" +
                "	edges.target AS target,\n" +
                "	edges.start AS starttime,\n" +
                "	edges.end AS endtime,\n" +
                "	edges.user AS user \n" +
                "FROM\n" +
                "	edges\n" +
                "WHERE\n" +
                "	edges.date > '%s' AND\n" +
                "	edges.date < '%s'", startDateTime, endDateTime);
//        
        System.out.println(nodeQuery);
        System.out.println(edgeQuery);
//        
//      
//        
        db.setEdgeQuery(edgeQuery);
        db.setNodeQuery(nodeQuery);
//        
//                db.setNodeQuery("SELECT \n" +
//"	news.id AS id, \n" +
//"	news.title AS label\n" +
//"FROM news LIMIT 6000");
        
//        db.setEdgeQuery("SELECT \n" +
//"	edges.source AS source, \n" +
//"	edges.target AS target,\n" +
//"	edges.start AS starttime,\n" +
//"	edges.end AS endtime,\n" +
//"	edges.user AS user \n" +
//"FROM edges");
        AttributeColumn edgesTimeIntervalColumn = acc.addAttributeColumn(attributeModel.getEdgeTable(), "Time Interval", AttributeType.TIME_INTERVAL);
        
        ImporterEdgeList edgeListImporter = new ImporterEdgeList();
        Container container = importController.importDatabase(db, edgeListImporter);
        container.setAllowAutoNode(false);      //Don't create missing nodes
        container.getLoader().setEdgeDefault(EdgeDefault.DIRECTED);   //Force DIRECTED
        
        //Append imported data to GraphAPI     
        importController.process(container, new DefaultProcessor(), workspace);
        
        //See if graph is well imported
        DirectedGraph graph = graphModel.getDirectedGraph();
        
        AttributeTable edgeTable = attributeModel.getEdgeTable();
        AttributeColumn startColumn = edgeTable.getColumn("starttime");
        AttributeColumn endColumn = edgeTable.getColumn("endtime");
        
        merger.mergeNumericColumnsToTimeInterval(edgeTable, startColumn, endColumn, 0, 30000);
        
        // Normalizing the weights (@Todo check why they are not all equal!)
        acc.fillEdgesColumnWithValue(graph.getEdges().toArray(), attributeModel.getEdgeTable().getColumn("weight"), "1");
        
        // Popularity of a node: Use InDegree ranking
        RankingController rankingController = Lookup.getDefault().lookup(RankingController.class);
        Ranking inDegreeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, Ranking.INDEGREE_RANKING);
        AbstractSizeTransformer sizeTransformer = (AbstractSizeTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_SIZE);
        sizeTransformer.setMinSize(1);
        sizeTransformer.setMaxSize(5);
        rankingController.transform(inDegreeRanking,sizeTransformer);
        
        // Color of an edge depending on the visitID
        PartitionController partitionController = Lookup.getDefault().lookup(PartitionController.class);
        Partition p = partitionController.buildPartition(attributeModel.getEdgeTable().getColumn("user"), graph);
        EdgeColorTransformer edgeColorTransformer = new EdgeColorTransformer();
        edgeColorTransformer.randomizeColors(p);
        partitionController.transform(p, edgeColorTransformer);
        
        DynamicGraph dynamicGraph = dynamicModel.createDynamicGraph(graph);

        // Print status
        System.out.println("All node columns:");
        for (AttributeColumn col : attributeModel.getNodeTable().getColumns()) {
            System.out.println(col.getTitle() + " - " + col.getType());
        }
        System.out.println();
        System.out.println("All edge columns:");
        for (AttributeColumn col : attributeModel.getEdgeTable().getColumns()) {
            System.out.println(col.getTitle() + " - " + col.getType());
        }
        System.out.println();
        System.out.println("Connection to database working!");
        System.out.println("Nodes: " + graph.getNodeCount());
        System.out.println("Edges: " + graph.getEdgeCount()); 
        System.out.println("Is dynamic graph? " + dynamicModel.isDynamicGraph());
        System.out.println("Min dynamic timestamp " + dynamicModel.getMin());
        System.out.println("Max dynamic timestamp " + dynamicModel.getMax());
        
        //Preview configuration
        PreviewController previewController = Lookup.getDefault().lookup(PreviewController.class);
        PreviewModelImpl previewModel = (PreviewModelImpl) previewController.getModel();
        previewModel.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.FALSE);
        previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_COLOR, new DependantOriginalColor(Color.BLACK));
        previewModel.getProperties().putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(Mode.ORIGINAL));
        previewModel.getProperties().putValue(PreviewProperty.EDGE_CURVED, Boolean.FALSE);
        previewModel.getProperties().putValue(PreviewProperty.EDGE_OPACITY, 100);
        previewModel.getProperties().putValue(PreviewProperty.EDGE_THICKNESS, 3);
        previewModel.getProperties().putValue(PreviewProperty.EDGE_LABEL_SHORTEN, Boolean.TRUE);
        previewModel.getProperties().putValue(PreviewProperty.BACKGROUND_COLOR, Color.gray);     
        
        // Load exporter
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        PNGExporter exporter = (PNGExporter) ec.getExporter("png");     //Get GEXF exporter
        exporter.setWorkspace(workspace); 
//        exporter.setHeight(1404);
//        exporter.setWidth(2496);
 
        // Create a new folder to store the images
        Date date = new Date();
        folder = new File("images-"+date.getTime());
        folder.mkdir();
        
        
        //Filter, remove degree < 10
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        DegreeRangeBuilder.DegreeRangeFilter degreeFilter = new DegreeRangeBuilder.DegreeRangeFilter();
        Query degreeQuery1 = filterController.createQuery(degreeFilter);
        Query degreeQuery2 = filterController.createQuery(degreeFilter);
        
        //Create a dynamic range filter query
        FilterBuilder[] builders = Lookup.getDefault().lookup(DynamicRangeBuilder.class).getBuilders();
        DynamicRangeFilter dynamicRangeFilter = (DynamicRangeFilter) builders[0].getFilter();     //There is only one TIME_INTERVAL column, so it's always the [0] builder
        DynamicRangeFilter dynamicGroupRangeFilter = (DynamicRangeFilter) builders[0].getFilter();
        
        Query dynamicGroupQuery = filterController.createQuery(dynamicGroupRangeFilter);
        Query dynamicQuery = filterController.createQuery(dynamicRangeFilter);
        
        //Set dynamic query as child of price query
        filterController.add(degreeQuery1);
        filterController.add(degreeQuery2);
        filterController.setSubQuery(degreeQuery1, dynamicQuery);
        filterController.setSubQuery(degreeQuery2, dynamicGroupQuery);
        
        // Layout the data using the Circular Layout Plugin and Noverlap
        RadialAxisLayout layout = new RadialAxisLayout(null, 300, false);
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        
        NoverlapLayout noverlabLayout = new NoverlapLayout(null);
        noverlabLayout.setGraphModel(graphModel);
        noverlabLayout.setSpeed(3.0);
        noverlabLayout.setMargin(10.0);
        noverlabLayout.setRatio(1.5);

        // Filter every
        
        long minTimeStamp = start;
        FileOutputStream stream = null;
        int i = 0;
        
        // how to find end time:
        
        
        long nextEnd = -1;
        
        // Filter graph according to
        GraphView view = null;
        previewController.refreshPreview();
        Dimension bigDimension = previewModel.getDimensions();
        Point topLeftPoint = previewModel.getTopLeftPosition();
        
        // Run through timeline and export images
        while(i < 15) {
            // Filter everything that is 
            
            try {
                // If next user, compute new 
                
                if(minTimeStamp > nextEnd) {
                    degreeFilter.setRange(new Range(1, 10));
                    dynamicGroupRangeFilter.setRange(new Range(minTimeStamp, minTimeStamp));
                    GraphView view2 = filterController.filter(degreeQuery2);
                    graphModel.setVisibleView(view2);
                    Thread.sleep(400);
                    
                    nextEnd = findNextEnd(graphModel);
                    
                    // Adjust filter
                    dynamicGroupRangeFilter.setRange(new Range(minTimeStamp, nextEnd));
                    GraphView view3 = filterController.filter(degreeQuery2);
                    graphModel.setVisibleView(view3);
                    Thread.sleep(400);
                    applyLayout(layout, noverlabLayout);
                    
                    // Store Dimension and topLeftPoint.
                    previewController.refreshPreview();
                    bigDimension = previewModel.getDimensions();
                    System.out.println("Dimensions: "+bigDimension);
                    topLeftPoint = previewModel.getTopLeftPosition();
                }
                
                // how to find out length of interval
                System.out.println("Percentage done: "+(Math.floor(minTimeStamp*100)/length)+"%");
                dynamicRangeFilter.setRange(new Range(minTimeStamp, minTimeStamp));
                
                view = filterController.filter(degreeQuery1);
                graphModel.setVisibleView(view);
                Thread.sleep(400);
                
                stream = new FileOutputStream(folder.toString()+String.format("/image%04d.png", i));
                // Set size
                previewController.refreshPreview(workspace);
                // Set new 
                previewModel.setDimensions(bigDimension);
                previewModel.setTopLeftPosition(topLeftPoint);
                
                PreviewProperties props = previewModel.getProperties();
                props.putValue("width", IMAGE_WIDTH);
                props.putValue("height", IMAGE_HEIGHT);
                ProcessingTarget target = (ProcessingTarget)previewController.getRenderTarget(RenderTarget.PROCESSING_TARGET);
                
                minTimeStamp = minTimeStamp + 1;
                i++;
                
                exportPNG(target, stream, null);
                
//                exporter.setOutputStream(stream);
//                exporter.execute();
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                try {
                    stream.close();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }   
    }
    
    private void exportPNG(ProcessingTarget target, FileOutputStream stream, ProgressTicket progress) {
        try {
            
            target.refresh();
            
            if (target instanceof LongTask) {
                ((LongTask) target).setProgressTicket(progress);
            }

            PGraphicsJava2D pg2 = (PGraphicsJava2D) target.getGraphics();
            BufferedImage img = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, pg2.pixels, 0, IMAGE_WIDTH);
            ImageIO.write(img, "png", stream);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void exportGEFX(ExportController ec, Workspace workspace) {
        GraphExporter exporter = (GraphExporter) ec.getExporter("gexf");     //Get GEXF exporter
        exporter.setExportVisible(true);  //Only exports the visible (filtered) graph
        exporter.setWorkspace(workspace);
        try {
            ec.exportFile(new File("new_io_gexf.gexf"), exporter);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }
    
    private void applyLayout(RadialAxisLayout radialAxisLayout, NoverlapLayout noverlabLayout) {
        radialAxisLayout.initAlgo();
        while(radialAxisLayout.canAlgo())
            radialAxisLayout.goAlgo();
        radialAxisLayout.endAlgo();
                
        // Apply NoverlapLayout
        noverlabLayout.initAlgo();
        while(noverlabLayout.canAlgo())
            noverlabLayout.goAlgo();
        noverlabLayout.endAlgo();
    }
    
    private int findNextEnd(GraphModel graphModel) {
        Graph filteredGraph = graphModel.getGraphVisible();
        Edge[] edges = filteredGraph.getEdges().toArray();
        
        Integer nextEnd = 0;
        
        for(Edge edge : edges) {
            System.out.println("Edge: "+edge.getSource()+" to: "+edge.getTarget());
            nextEnd = (Integer) edge.getEdgeData().getAttributes().getValue("endtime");
        }
        
        return nextEnd;
    }
    
    private void createMovie() {
        String path = folder.getAbsolutePath().toString();
        
        String[] cmdArgs = new String[] { "/usr/local/bin/ffmpeg", "-f", "image2", "-r", "1", "-i", path+"/image%04d.png", "-c:v", "libx264", "-r", "30", path+"/video.mp4" };
        
        try {
            Process p = Runtime.getRuntime().exec(cmdArgs);
            InputStream in = p.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            BufferedReader bre = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            
            String line;
            while(null != (line = reader.readLine())) {
                System.out.println(line);
            }
            
            while(null != (line = bre.readLine())) {
                System.out.println(line);
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
    
}
