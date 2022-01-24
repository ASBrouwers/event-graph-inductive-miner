package EventGraphs.Neo4j.ProcessDiscovery.InductiveMiner;

import EventGraphs.Neo4j.ProcessDiscovery.DiscoveryAlgorithm;
import org.neo4j.driver.Record;
import org.neo4j.driver.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.neo4j.driver.Values.parameters;

public class InductiveMiner extends DiscoveryAlgorithm {
    String minerName = "InductiveMiner";
    String modelName;
    Map<String, Long> time_division;
    int rootId;

    boolean logSplit = false;
    boolean minTrace = false;
    boolean infrequent = false;
    boolean filter = false;
    double ratio = 0.2;
    boolean DEBUG = false;
    long lastTime;
    Map<String, String> model_params;

    public InductiveMiner(Driver drv) {
        super(drv);
    }

    public String getModelName() {
        return modelName;
    }

    // --- UTIL ---

    // Load query from file
    String loadQuery(String path) {
        try {
            String result = Files.readString(Paths.get(path));
            return result;
        } catch (Exception e) {
            System.err.println(e.toString());
            return null;
        }
    }

    String getQueryPath(String queryName) {
        return String.format("src/EventGraphs/Neo4j/ProcessDiscovery/%s/queries/%s", minerName, queryName);
    }


    // --- MODEL GENERATION ---

    @Override
    public void generatePetriNet() {
        try (Session session = driver.session()) {
            session.writeTransaction(this::createPetriNet);
        }
    }

    private int createPetriNet(Transaction tx) {
        long before_time = System.currentTimeMillis();
        String initPN = loadQuery(getQueryPath("petri/init_petri.cypher"));
        String PNexc = loadQuery(getQueryPath("petri/exc_petri.cypher"));
        String PNseq = loadQuery(getQueryPath("petri/seq_petri.cypher"));
        String PNpar = loadQuery(getQueryPath("petri/par_petri.cypher"));
        String PNloop = loadQuery(getQueryPath("petri/loop_petri.cypher"));
        String nextNode = loadQuery(getQueryPath("petri/next_petri_node.cypher"));
        String s_e = loadQuery(getQueryPath("petri/set_start_end.cypher"));
        String tau = loadQuery(getQueryPath("petri/convert_tau.cypher"));

        Map<String, String> typeQuery = new HashMap<>();
        typeQuery.put("Exclusive", PNexc);
        typeQuery.put("Sequence", PNseq);
        typeQuery.put("Parallel", PNpar);
        typeQuery.put("Loop", PNloop);

        tx.run(initPN, parameters("ptNode", rootId));

        Result nextPT = tx.run(nextNode, parameters("ptNode", rootId));

        while (nextPT.hasNext()) {
            List<Record> next = nextPT.list();
            for (Record r : next) {
                String type = r.get("par").get("type").asString();
                if (DEBUG) {
                    System.out.println("type = " + type + ", ID: " + r.get("par"));
                }
                tx.run(typeQuery.get(type), parameters("ptNode", r.get("id").asInt()));
            }
            nextPT = tx.run(nextNode, parameters("ptNode", rootId));
        }

        tx.run(s_e, parameters("ptNode", rootId));
        tx.run(tau, parameters("ptNode", rootId));
        long after_time = System.currentTimeMillis();
        time_division.merge("petri", after_time - before_time, (a, b) -> (a + b));

        if (DEBUG) {
            System.out.println("time_division = " + time_division.toString());
        }
        return 1;
    }

    @Override
    public void generateProcessModel(Map<String, String> modelParams) {
        this.model_params = modelParams;

        try (Session session = driver.session()) {
            String eType = modelParams.get("dfType");
            String cType = modelParams.get("className");
            boolean logSplit = Boolean.parseBoolean(modelParams.get("logSplit"));
            boolean minTrace = Boolean.parseBoolean(modelParams.get("minTrace"));
            ratio = Double.parseDouble(modelParams.get("filter"));
            infrequent = ratio > 0;

            this.logSplit = logSplit;
            this.minTrace = minTrace;

            System.out.println("modelParams = " + modelParams.toString());
            long startTime = System.currentTimeMillis();
            lastTime = System.currentTimeMillis();
            int modelId = session.writeTransaction(tx -> setupMiner(tx, modelParams.get("logName"), eType, cType, ratio));
            setTime("setup");
            runAlgorithm(session, modelId, eType, cType);
            long endTime = System.currentTimeMillis();
            System.out.println("Execution time for model " + modelName + " : " + (endTime - startTime) / 1000.0 + " seconds");
        }
    }

    // --- INDUCTIVE MINER STEPS

    public void runAlgorithm(final Session session, int modelId, String eType, String cType) {
        Optional<Integer> nextCut = session.readTransaction(tx -> getNextCutId(tx, modelId));
        while (nextCut.isPresent()) {
            searchCut(session, nextCut.get(), eType, cType);
            nextCut = session.readTransaction(tx -> getNextCutId(tx, modelId));
        }
    }

    private Record searchCut(final Session session, int parentCut, String eType, String cType) {
        return searchCut(session, parentCut, eType, cType, false);
    }

    private Record searchCut(final Session session, int parentCut, String eType, String cType, boolean second) {
        System.out.println("[IMSTEP] STARTED SEARCHING CUT: " + parentCut);
        if (DEBUG) {
            System.out.println("infrequent = " + infrequent);
            System.out.println("ratio = " + ratio);
            System.out.println("parentCut = " + parentCut);
            System.out.println("second = " + second);
        }

        session.writeTransaction(tx -> transferParentModel(tx, parentCut));
        if (logSplit || parentCut == rootId) {
            System.out.println("    [IMSTEP] Creating DFG from log: " + parentCut);

            session.writeTransaction(tx -> createLogDFG(tx, parentCut, eType, cType));
            setTime("createDFG");

            System.out.println("    [IMSTEP] Finished creating DFG");
            if (DEBUG) {
                System.out.println("parentCut = " + parentCut);
                System.out.println("rootId = " + rootId);
            }

            if (second) {
                System.out.println("    [INFREQUENT] filtering DFG");
                session.writeTransaction(tx -> filterDFG(tx, parentCut, ratio));
                setTime("filterDFG");
            }
        }

        String countClasses = loadQuery(getQueryPath("logSplit/countClasses.cypher"));
        Result resCC = session.run(countClasses, parameters("ptId", parentCut));
        int count = resCC.next().get("classCount").asInt();

        if (count <= 1) {
            boolean quit = session.writeTransaction(tx -> handlePTSingleton(tx, parentCut, eType, cType));
            if (quit) return null;
        }

        if (parentCut == rootId && filter) {
            session.writeTransaction(tx -> filterDFG(tx, parentCut, ratio));
            setTime("filterDFG");
        }

        Record excRecord = searchExclusiveCut(session, parentCut, eType, cType, second);

        if (excRecord != null) return excRecord;

        System.out.println("    [IMSTEP] FINDING SEQUENCE CUT: " + parentCut);
        int stepIdSeq = session.writeTransaction(tx -> prepSequence(tx, parentCut, eType, cType, second));
        session.writeTransaction(tx -> findSequenceCut(tx, parentCut, stepIdSeq, eType, cType));
        setTime("searchSeq");

        int seqCount = session.readTransaction(tx -> countComponents(tx, stepIdSeq));
        if (DEBUG) {
            System.out.println("stepIdSeq = " + stepIdSeq);
            System.out.println("seqCount = " + seqCount);
        }
        if (seqCount > 1) {

            System.out.println("    [IMSTEP] FOUND SEQUENCE CUT");
            if (logSplit) {
                System.out.println("Splitting log...");
                session.writeTransaction(tx -> splitLogSeq(tx, parentCut, stepIdSeq, eType, cType, second));
            } else {
                session.writeTransaction(tx -> transferCompSequence(tx, parentCut, eType, cType, second));
                session.writeTransaction(tx -> startEndSequence(tx, parentCut, eType, cType));
            }
            setTime("splitSeq");
            return null;
        }

        System.out.println("    [IMSTEP] FINDING PARALLEL CUT: " + parentCut);

        Record parPrepResult = session.writeTransaction(tx -> prepParallelCut(tx, parentCut, eType, cType, second));
        int stepIdPar = parPrepResult.get("stepId").asInt();
        Record parResult = session.writeTransaction(tx -> findParallelCut(tx, parentCut, eType, cType, stepIdPar, second));
        setTime("searchPar");
        if (parResult.get("result").asInt() == 1) {
            if (session.readTransaction(tx -> checkParallelCut(tx, stepIdPar, cType))) {
                System.out.println("    [IMSTEP] FOUND PARALLEL CUT: " + parResult.get("stepId").asInt());
                if (logSplit) {
                    System.out.println("Splitting log...");
                    session.writeTransaction(tx -> splitLogPar(tx, parentCut, stepIdPar, eType, cType));
                } else {
                    session.writeTransaction(tx -> transferComponents(tx, parResult.get("stepId").asInt(), eType, cType));
                    session.writeTransaction(tx -> transferStartEnd(tx, parentCut, cType));
                }
                setTime("splitPar");
                return parResult;
            }
        }

        System.out.println("    [IMSTEP] FINDING LOOP CUT: " + parentCut);

        if (session.readTransaction(tx -> loopPrecheck(tx, parentCut, cType)) > 0) {
            Record loopResult = session.writeTransaction(tx -> prepLoopCut(tx, parentCut, eType, cType, second));
            int stepIdLoop = loopResult.get("stepId").asInt();
            session.writeTransaction(tx -> findLoopCut(tx, loopResult.get("stepId").asInt(), eType));
            boolean loopCheck = session.writeTransaction(tx -> checkLoopConditions(tx, stepIdLoop, eType, cType));
            int compCount = session.writeTransaction(tx -> countComponents(tx, stepIdLoop));

            setTime("searchLoop");

            if (loopCheck && (compCount > 1)) {
                System.out.println("    [IMSTEP] FOUND LOOP CUT: " + stepIdLoop);
                if (DEBUG) {
                    System.out.println(":params {stepId: " + stepIdLoop + ", ptId: " + parentCut + ", entityType: " + eType + ", classType: " + cType + "}");
                }
                if (logSplit) {
                    System.out.println("Splitting log...");
                    session.writeTransaction(tx -> splitLogLoop(tx, parentCut, stepIdLoop, eType, cType));
                } else {
                    session.writeTransaction(tx -> transferComponents(tx, stepIdLoop, eType, cType));
                    session.writeTransaction(tx -> startEndLoop(tx, stepIdLoop, eType));
                }
                setTime("splitLoop");
                return loopResult;
            }
        }

        System.out.println("NO VALID CUT FOUND");
        if (infrequent && !second) {
            return searchCut(session, parentCut, eType, cType, true);
        }
        session.writeTransaction(tx -> fallback(tx, parentCut, cType));
        System.out.println("Gave up on node: " + parentCut);
        return null;
    }

    private Record searchExclusiveCut(final Session session, int parentCut, String eType, String cType, boolean second) {
        System.out.println("    [IMSTEP] FINDING EXCLUSIVE CUT: " + parentCut);
        Record exResult = session.writeTransaction(tx -> findExclusiveCut(tx, parentCut, eType, cType, second));
        setTime("searchExc");
        if (exResult.get("result").asInt() == 1) {
            int stepIdExc = exResult.get("stepId").asInt();
            System.out.println("    [IMSTEP] FOUND EXCLUSIVE CUT: " + stepIdExc);
            if (logSplit) {
                System.out.println("Splitting log...");
                session.writeTransaction(tx -> splitLogExc(tx, parentCut, stepIdExc, eType, cType));
            } else {
                System.out.println("Splitting DFG...");
                session.writeTransaction(tx -> transferComponents(tx, stepIdExc, eType, cType));
                session.writeTransaction(tx -> transferStartEnd(tx, parentCut, cType));
            }
            setTime("splitExc");
            return exResult;
        }
        return null;
    }

    private void setTime(String timeType) {
        long prevTime = lastTime;
        lastTime = System.currentTimeMillis();
        time_division.merge(timeType, lastTime - prevTime, (original, toAdd) -> original + toAdd);
    }


    // --- QUERY TRANSACTIONS
    private int setupMiner(final Transaction tx, String logName, String eType, String cType, double filter) {
        System.out.println("logName = " + logName);
        String qInit;
        qInit = loadQuery(getQueryPath("logSplit/setupLog.cypher"));

        if (time_division == null) time_division = new HashMap<>();

        String split = logSplit ? "Log" : "DFG";

        Record r = tx.run(qInit, parameters("logName", logName, "entityType", eType, "classType", cType, "filtering", filter >= 0, "freq", filter, "split", split)).next();

        rootId = r.get("root").asInt();

        modelName = r.get("modelName").asString();
        return r.get("modelId").asInt();
    }

    private int filterDFG(final Transaction tx, int ptId, double ratio) {
        String qFilter = loadQuery(getQueryPath("common/filter_DFG.cypher"));

        Result r = tx.run(qFilter, parameters("ratio", ratio, "ptId", ptId));
        int removedCount = r.next().get("count").asInt();

        System.out.println("Done filtering DFG, removed " + removedCount + " relationships");
        return removedCount;
    }


    // --- Base case detection ---
    private boolean handlePTSingleton(final Transaction tx, int ptId, String eType, String cType) {
        String baseCase;
        if (infrequent && false) {
            baseCase = loadQuery(getQueryPath("common/base_case_infrequent.cypher"));
        } else {
            baseCase = loadQuery(getQueryPath("common/base_case.cypher"));
        }
        Result result = tx.run(baseCase, parameters("ptId", ptId, "ratio", 0.1));
        if (DEBUG) System.out.println("result = " + result);
        return result.next().get("quit").asBoolean();
    }

    // --- Finding cuts ---
    private Optional<Integer> getNextCutId(final Transaction tx, int modelId) {
        String qActiveNode = loadQuery(getQueryPath("common/IMactiveNode.cypher"));
        Result result = tx.run(qActiveNode, parameters("modelId", modelId));
        if (result.hasNext()) {
            return Optional.of(result.next().get("id").asInt());
        }
        return Optional.empty();
    }

    private Record findExclusiveCut(final Transaction tx, int parentCut, String eType, String cType, boolean filtered) {
        String qGraph = loadQuery(getQueryPath("common/create_PTnode_DFG_graph.cypher"));
        String wcc = loadQuery(getQueryPath("common/findWCC.cypher"));

        tx.run(qGraph, parameters("ptId", parentCut, "graphName", "DFCgraph", "entityType", eType, "classType", cType));
        Record r = tx.run(wcc, parameters("ptId", parentCut, "graphName", "DFCgraph", "cutType", "Exclusive", "filtered", filtered)).next();
        return r;
    }


    private int prepSequence(final Transaction tx, int parentCut, String eType, String cType, boolean filtered) {
        if (DEBUG) System.out.println("ptId: " + parentCut + ", entityType: " + eType + ", classType: " + cType);
        String createComps = loadQuery(getQueryPath("sequence/create_component_nodes.cypher"));

        Record result = tx.run(createComps, parameters("ptId", parentCut, "entityType", eType, "classType", cType, "filtered", filtered)).next();
        int stepId = result.get("stepId").asInt();
        return stepId;
    }

    private int findSequenceCut(final Transaction tx, int parentCut, int stepId, String eType, String cType) {
        if (DEBUG) System.out.println("ptId: " + parentCut + ", entityType: " + eType + ", classType: " + cType);

        String seqGraph = loadQuery(getQueryPath("sequence/create_seq_graph.cypher"));
        String mReachable = loadQuery(getQueryPath("sequence/merge_reachable.cypher"));
        String mUnreachable = loadQuery(getQueryPath("sequence/merge_unreachable.cypher"));
        String mSkips = loadQuery(getQueryPath("sequence/merge_skips.cypher"));
        String removeLoops = loadQuery(getQueryPath("sequence/remove_component_loops.cypher"));

        tx.run(seqGraph, parameters("stepId", stepId));
        tx.run(mReachable, parameters("stepId", stepId));

        tx.run(mUnreachable, parameters("stepId", stepId));
        tx.run(removeLoops, parameters("ptId", parentCut));
        if (DEBUG) System.out.println(":params {stepId: " + stepId + ", classType: " + cType + "}");
        Result result = tx.run(removeLoops, parameters("ptId", parentCut));
        tx.run(mSkips, parameters("stepId", stepId, "classType", cType));
        return 1;
    }


    private Record transferCompSequence(final Transaction tx, int parentCut, String eType, String cType, boolean filtered) {
        String q5 = loadQuery(getQueryPath("sequence/transfer_component_pt_seq.cypher"));
        return tx.run(q5, parameters("ptId", parentCut, "classType", cType, "entityType", eType, "filtered", filtered)).next();
     }

    private Record startEndSequence(final Transaction tx, int parentCut, String eType, String cType) {
        String query = loadQuery(getQueryPath("sequence/start_end_sequence.cypher"));
        String ends = loadQuery(getQueryPath("sequence/start_end_sequence_ends.cypher"));
        tx.run(ends, parameters("ptId", parentCut, "classType", cType));
        return tx.run(query, parameters("ptId", parentCut, "entityType", eType)).next();
    }

    private Record prepParallelCut(final Transaction tx, int parentCut, String eType, String cType, boolean filtered) {
        String setupInv = loadQuery(getQueryPath("parallel/createInvertedGraph.cypher"));
        Result r = tx.run(setupInv, parameters("ptId", parentCut, "graphName", "iGraph", "entityType", eType, "classType", cType, "filtered", filtered));
        return r.next();
    }

    private Record findParallelCut(final Transaction tx, int parentCut, String eType, String cType, int stepId, boolean filtered) {
        String setupGraph = loadQuery(getQueryPath("parallel/createGDSInvertedGraph.cypher"));
        String wcc = loadQuery(getQueryPath("common/findWCC.cypher"));
        String link = loadQuery(getQueryPath("parallel/link_component_class.cypher"));
        tx.run(setupGraph, parameters("ptId", parentCut, "stepId", stepId, "graphName", "iDFG", "entityType", eType));
        Record r = tx.run(wcc, parameters("ptId", parentCut, "stepId", stepId, "graphName", "iDFG", "cutType", "Parallel", "filtered", filtered)).next();
        tx.run(link, parameters("stepId", r.get("stepId").asInt(), "classType", cType));
        return r;
    }

    private boolean checkParallelCut(final Transaction tx, int stepId, String cType) {
        String checkStart = loadQuery(getQueryPath("parallel/check_par_condition_start.cypher"));
        String checkEnd = loadQuery(getQueryPath("parallel/check_par_condition_end.cypher"));
        int startViolations = tx.run(checkStart, parameters("stepId", stepId, "classType", cType)).next().get("violations").asInt();
        int endViolations = tx.run(checkEnd, parameters("stepId", stepId, "classType", cType)).next().get("violations").asInt();
        return startViolations < 1 && endViolations < 1;
    }


    private int loopPrecheck(final Transaction tx, int parentCut, String cType) {
        String precheck = loadQuery(getQueryPath("loop/loop_precheck.cypher"));
        return tx.run(precheck, parameters("ptId", parentCut, "classType", cType)).next().get("count").asInt();
    }

    private Record prepLoopCut(final Transaction tx, int parentCut, String eType, String cType, boolean filtered) {
        String initComponent = loadQuery(getQueryPath("loop/create_loop_components.cypher"));
        String wcc = loadQuery(getQueryPath("common/findWCC.cypher"));

        tx.run(initComponent, parameters("ptId", parentCut, "graphName", "loopGraph", "entityType", eType, "classType", cType));
        Record wccResult = tx.run(wcc, parameters("ptId", parentCut, "graphName", "loopGraph", "cutType", "Loop", "filtered", filtered)).next();
        int stepId = wccResult.get("stepId").asInt();
        return wccResult;
    }

    private int findLoopCut(final Transaction tx, int stepId, String eType) {
        String mainBody = loadQuery(getQueryPath("loop/set_main_start.cypher"));
        String mainBody2 = loadQuery(getQueryPath("loop/set_main_end.cypher"));
        String mainFalse = loadQuery(getQueryPath("loop/set_main_false.cypher"));
        tx.run(mainFalse, parameters("stepId", stepId));
        tx.run(mainBody, parameters("stepId", stepId, "entityType", eType));
        tx.run(mainBody2, parameters("stepId", stepId, "entityType", eType));
        return 1;
    }

    private boolean checkLoopConditions(final Transaction tx, int stepNode, String eType, String cType) {
        String c1 = loadQuery(getQueryPath("loop/check_loop_condition_5.cypher"));
        String c2 = loadQuery(getQueryPath("loop/check_loop_condition_6.cypher"));
        int r1 = tx.run(c1, parameters("stepId", stepNode, "entityType", eType, "classType", cType)).next().get("violated").asInt();
        int r2 = tx.run(c2, parameters("stepId", stepNode, "entityType", eType, "classType", cType)).next().get("violated").asInt();
        return r1 == 0 && r2 == 0;
    }

    // Finalizing cuts


    private int transferParentModel(final Transaction tx, int ptid) {
        String qTransfer = loadQuery(getQueryPath("logSplit/transfer_parent_model.cypher"));
        tx.run(qTransfer, parameters("ptId", ptid));
        return 1;
    }


    private int countComponents(final Transaction tx, int stepNode) {
        String count = loadQuery(getQueryPath("common/count_components.cypher"));
        Result result = tx.run(count, parameters("stepId", stepNode));
        return result.next().get("count").asInt();
    }

    private int transferComponents(final Transaction tx, final int stepId, String eType, String cType) {
        String splitQ = loadQuery(getQueryPath("common/transfer_component_pt.cypher"));
        tx.run(splitQ, parameters("stepId", stepId, "classType", cType, "entityType", eType));
        return 1;
    }

    private int transferStartEnd(final Transaction tx, int parentCut, String cType) {
        String startEnd = loadQuery(getQueryPath("common/transfer_start_end.cypher"));
        tx.run(startEnd, parameters("ptId", parentCut, "classType", cType));
        return 1;
    }

    private int startEndLoop(final Transaction tx, int stepId, String eType) {
        String q = loadQuery(getQueryPath("loop/start_end_loop.cypher"));

        tx.run(q, parameters("stepId", stepId, "entityType", eType));
        return 1;
    }

    private int fallback(final Transaction tx, int parentCut, String cType) {
        String fallbackQ = loadQuery(getQueryPath("loop/fallback_loop.cypher"));
        tx.run(fallbackQ, parameters("ptId", parentCut, "classType", cType));
        return 1;
    }

    // Log splits

    private int createLogDFG(final Transaction tx, int ptId, String eType, String cType) {
        if (DEBUG) {
            System.out.println(":params {ptId: " + ptId + ", entityType: " + eType + ", classType: " + cType + "}");
        }
        String parentModel = loadQuery(getQueryPath("logSplit/transfer_parent_model.cypher"));
        String qCreateDFG = loadQuery(getQueryPath("logSplit/createLogDFG.cypher"));
        tx.run(parentModel, parameters("ptId", ptId, "entityType", eType, "classType", cType));
        tx.run(qCreateDFG, parameters("ptId", ptId, "entityType", eType, "classType", cType));
        return 1;
    }

    private int splitLogExc(final Transaction tx, int ptId, int stepId, String eType, String cType) {
        if (DEBUG) System.out.println(":params {ptId: " + ptId + ", stepId: " + stepId + "}");
        String qSplitLogExc;
        if (infrequent || true) {
            qSplitLogExc = loadQuery(getQueryPath("logSplit/splitLogExclusive_infrequent.cypher"));
        } else {
            qSplitLogExc = loadQuery(getQueryPath("logSplit/splitLogExclusive.cypher"));
        }
        String createWorkingCopy = loadQuery(getQueryPath("logSplit/create_log_copy.cypher"));
        String removeWorkingCopy = loadQuery(getQueryPath("logSplit/delete_log_copy.cypher"));
        String removeViolations = loadQuery(getQueryPath("logSplit/remove_violating_events.cypher"));

        tx.run(createWorkingCopy, parameters("ptId", ptId));
        tx.run(qSplitLogExc, parameters("ptId", ptId, "stepId", stepId, "entityType", eType, "classType", cType));
        if (infrequent) {
            tx.run(removeViolations, parameters("stepId", stepId));
        }
        tx.run(removeWorkingCopy, parameters("ptId", ptId, "entityType", eType));
        return 1;
    }


    private int splitLogSeq(final Transaction tx, int ptId, int stepId, String eType, String cType, boolean second) {
        String qSplitLogSeq;
        if (infrequent && second) {
            qSplitLogSeq = loadQuery(getQueryPath("logSplit/findOptimalSequenceSplit.cypher"));
        } else {
            qSplitLogSeq = loadQuery(getQueryPath("logSplit/splitLogSequence.cypher"));
        }
        String createWorkingCopy = loadQuery(getQueryPath("logSplit/create_log_copy.cypher"));
        String removeWorkingCopy = loadQuery(getQueryPath("logSplit/delete_log_copy.cypher"));
        String ptSeq = loadQuery(getQueryPath("logSplit/transfer_pt_seq.cypher"));
        String addEmptyTraces = loadQuery(getQueryPath("logSplit/add_empty_traces_sequence.cypher"));
        String addTauTraces = loadQuery(getQueryPath("logSplit/add_tau_traces_sequence.cypher"));

        tx.run(createWorkingCopy, parameters("ptId", ptId));
        if (DEBUG) {
            System.out.printf(":params {ptId: %s, stepId: %s, entityType: %s, classType: %s}", ptId, stepId, eType, cType);
        }
        tx.run(qSplitLogSeq, parameters("ptId", ptId, "stepId", stepId, "entityType", eType, "classType", cType));
        tx.run(removeWorkingCopy, parameters("ptId", ptId, "entityType", eType));
        tx.run(addEmptyTraces, parameters("ptId", ptId, "entityType", eType));
        tx.run(addTauTraces, parameters("stepId", stepId, "entityType", eType));
        tx.run(ptSeq, parameters("stepId", stepId));

        return 1;
    }

    private Record splitLogPar(final Transaction tx, int ptId, int stepId, String eType, String cType) {
        if (DEBUG) System.out.println(":params {ptId: " + ptId + ", stepId: " + stepId + "}");
        String qSplitLogPar = loadQuery(getQueryPath("logSplit/splitLogParallel.cypher"));
        String createWorkingCopy = loadQuery(getQueryPath("logSplit/create_log_copy.cypher"));
        String removeWorkingCopy = loadQuery(getQueryPath("logSplit/delete_log_copy.cypher"));
        tx.run(createWorkingCopy, parameters("ptId", ptId));
        Result r = tx.run(qSplitLogPar, parameters("ptId", ptId, "stepId", stepId, "entityType", eType, "classType", cType));
        tx.run(removeWorkingCopy, parameters("ptId", ptId, "entityType", eType));
        return r.next();
    }

    private Record splitLogLoop(final Transaction tx, int ptId, int stepId, String eType, String cType) {
        String qSplitLogLoop = loadQuery(getQueryPath("logSplit/splitLogLoop.cypher"));
        String createWorkingCopy = loadQuery(getQueryPath("logSplit/create_log_copy.cypher"));
        String removeWorkingCopy = loadQuery(getQueryPath("logSplit/delete_log_copy.cypher"));
        String addEmptyTraces = loadQuery(getQueryPath("logSplit/add_empty_traces_loop.cypher"));
        tx.run(createWorkingCopy, parameters("ptId", ptId));
        Result r = tx.run(qSplitLogLoop, parameters("ptId", ptId, "stepId", stepId, "entityType", eType, "classType", cType));
        tx.run(removeWorkingCopy, parameters("ptId", ptId, "entityType", eType));
        tx.run(addEmptyTraces, parameters("ptId", ptId, "entityType", eType));
        return r.next();
    }





}
