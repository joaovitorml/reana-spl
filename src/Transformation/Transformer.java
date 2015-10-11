package Transformation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import tool.RDGNode;
import Parsing.Node;
import Parsing.ActivityDiagrams.ADReader;
import Parsing.ActivityDiagrams.Activity;
import Parsing.ActivityDiagrams.ActivityType;
import Parsing.ActivityDiagrams.Edge;
import Parsing.Exceptions.InvalidNodeClassException;
import Parsing.Exceptions.InvalidNodeType;
import Parsing.Exceptions.InvalidNumberOfOperandsException;
import Parsing.SequenceDiagrams.Fragment;
import Parsing.SequenceDiagrams.Message;
import Parsing.SequenceDiagrams.MessageType;
import Parsing.SequenceDiagrams.Operand;
import fdtmc.FDTMC;
import fdtmc.State;

public class Transformer {
	// Attributes

	private HashMap<String, FDTMC> fdtmcByName;
	private HashMap<String, Integer> nCallsByName;
	private HashMap<String, State> stateByActID;
	private int parNum;
	private int loopNum;

	// Constructors

	public Transformer () {
		fdtmcByName = new HashMap<String, FDTMC>();
		nCallsByName = new HashMap<String, Integer>();
	}

	// Relevant public methods

	/**
	 * Transforms an AD to a fDTMC
	 * @param adParser
	 */
	public RDGNode transformSingleAD(ADReader adParser) {
		FDTMC fdtmc = new FDTMC();

		fdtmc.setVariableName("s" + adParser.getName());
		fdtmcByName.put(adParser.getName(), fdtmc);
		nCallsByName.put(adParser.getName(), 1);

		stateByActID = new HashMap<String, State>();
		State init = fdtmc.createState("initial");
        State error = fdtmc.createState("error");

		transformPath(fdtmc, init, error, adParser.getActivities().get(0).getOutgoing().get(0));
		System.out.println(fdtmc.toString());

		// The method currently does not support variability in ADs.
		RDGNode node = new RDGNode(adParser.getName(),
		                           "true",
		                           fdtmc);
		return node;
	}

	/**
	 * Transform an SD to a fDTMC
	 * @param sdParser
	 * @throws InvalidNumberOfOperandsException
	 * @throws InvalidNodeClassException
	 */
	public RDGNode transformSingleSD(Fragment fragment) throws InvalidNumberOfOperandsException, InvalidNodeClassException, InvalidNodeType {
		boolean isNew = checkNew (fragment.getName());

		countCallsModel (fragment.getName());

		if (!isNew) { /* Fragmento ja foi modelado */
			return RDGNode.getById(fragment.getName());
		}

		FDTMC fdtmc = new FDTMC();
		State init, error, source;
		parNum = 0;
		loopNum = 0;

		if (fragment.getName() != null && !fragment.getName().isEmpty()) {
			fdtmc.setVariableName("s" + fragment.getName());
			fdtmcByName.put(fragment.getName(), fdtmc);
		} else {
			fdtmc.setVariableName("s" + ((Operand)fragment.getNodes().get(0)).getGuard());
			fdtmcByName.put(((Operand)fragment.getNodes().get(0)).getGuard(), fdtmc);
		}
		init = fdtmc.createState("initial");
		error = fdtmc.createState("error");
		source = init;

		RDGNode rdgNode = new RDGNode(fragment.getName(), "true", fdtmc);
		transformFDTMCNodes(fdtmc, fragment.getNodes(), source, error, rdgNode);

		System.out.println(fdtmc.toString());
		return rdgNode;
	}

	public void transformFDTMCNodes(FDTMC fdtmc, ArrayList<Node> nodes, State source, State error, RDGNode currentRDGNode) throws InvalidNumberOfOperandsException, InvalidNodeClassException, InvalidNodeType {
		int i = 1;
		for (Node n : nodes) {
			if (i++ >= nodes.size()) {
				State success = fdtmc.createState("success");
				if (n.getClass().equals(Message.class)) {
					transformMessage(fdtmc, (Message)n, source, success, error);
				} else if (n.getClass().equals(Fragment.class)) {
					transformFragment(fdtmc, (Fragment)n, source, success, error, currentRDGNode);
				}
			} else {
				if (n.getClass().equals(Message.class)) {
					source = transformMessage(fdtmc, (Message)n, source, fdtmc.createState(), error);
				} else if (n.getClass().equals(Fragment.class)) {
					source = transformFragment(fdtmc, (Fragment)n, source, fdtmc.createState(), error, currentRDGNode);
				}
			}
		}
	}

	public void transformFDTMCNodes(FDTMC fdtmc, ArrayList<Node> nodes, State source, State target, State error, RDGNode currentRdgNode) throws InvalidNumberOfOperandsException, InvalidNodeClassException, InvalidNodeType {
		int i = 1;
		for (Node n : nodes) {
			if (i++ >= nodes.size()) {
				if (n.getClass().equals(Message.class)) {
					transformMessage(fdtmc, (Message)n, source, target, error);
				} else if (n.getClass().equals(Fragment.class)) {
					transformFragment(fdtmc, (Fragment)n, source, target, error, currentRdgNode);
				}
			} else {
				if (n.getClass().equals(Message.class)) {
					source = transformMessage(fdtmc, (Message)n, source, fdtmc.createState(), error);
				} else if (n.getClass().equals(Fragment.class)) {
					source = transformFragment(fdtmc, (Fragment)n, source, fdtmc.createState(), error, currentRdgNode);
				}
			}
		}
	}

	// Relevant private methods

	/**
	 * Augments the fDTMC with $msg information
	 * @param fdtmc
	 * @param msg: the message
	 * @param source: the fDTMC node that triggers the message
	 * @param target: the fDTMC node that the message should go to
	 * @param error: the error state where message transmission failure should be transited to
	 * @return the $target itself, the point in the fDTMC where the execution of the message will stop at
	 */
	private State transformMessage(FDTMC fdtmc, Message msg, State source, State target, State error) {
		BigDecimal a = new BigDecimal("1.0");
		BigDecimal b = new BigDecimal(Float.toString(msg.getProb()));

		if (msg.getType().equals(MessageType.asynchronous)) {
			fdtmc.createTransition(source, target, "", b.toString());
			fdtmc.createTransition(source, error, "", a.subtract(b).toString());
		} else { /* Mensagem sincrona */
			fdtmc.createTransition(source, target, msg.getName(), b.toString());
			fdtmc.createTransition(source, error, msg.getName(), a.subtract(b).toString());
		}
		return target;
	}

	/**
	 * Distributes the fragment transformation method calls based on the the type of the Fragment
	 * @throws InvalidNumberOfOperandsException
	 * @throws InvalidNodeClassException
	 */
	private State transformFragment(FDTMC fdtmc, Fragment fragment, State source, State target, State error, RDGNode currentRdgNode) throws InvalidNumberOfOperandsException, InvalidNodeClassException, InvalidNodeType {
		switch(fragment.getType()) {
			case loop:
				return transformLoopFragment(fdtmc, fragment, source, target, error, currentRdgNode);
			case alternative:
				return transformAltFragment(fdtmc, fragment, source, target, error, currentRdgNode);
			case optional:
				return transformOptFragment(fdtmc, fragment, source, target, error, currentRdgNode);
			case parallel:
				return transformParallelFragment(fdtmc, fragment, source, target, error, currentRdgNode);
			default:
				break;
		}
		return null;
	}

	/**
	 * Recursively augments the fDTMC with $fragments information
	 * @param fdtmc
	 * @param fragment: an fragment of type loop
	 * @param source: the fDTMC node that triggers or not the Fragment
	 * @param target: the fDTMC node that the fragment should return to
	 * @param error: the error state where message transmission failure should be transited to
	 * @return the $target itself, the point in the fDTMC where the execution or not of this $fragment will transit to
	 * @throws InvalidNumberOfOperandsException
	 * @throws InvalidNodeClassException
	 */
	private State transformLoopFragment(FDTMC fdtmc, Fragment fragment, State source, State target, State error, RDGNode currentRdgNode) throws InvalidNumberOfOperandsException, InvalidNodeClassException, InvalidNodeType {
		if (fragment.getNodes().size() > 1) throw new InvalidNumberOfOperandsException("A Loop fragment can only have 1 operand!");

		Operand operand = (Operand)fragment.getNodes().get(0);
		String name = (fragment.getName() != null & !fragment.getName().isEmpty()) ? fragment.getName() : "Loop" + ++loopNum;
		State opStart = fdtmc.createState("initial" + name);
		State opEnd = fdtmc.createState("end" + name);

		// TODO Assuming for now that loop/not-loop probability is 50/50.
		String loopProbability = "0.5";

		fdtmc.createTransition(source, target, "", "1 - " + loopProbability); // not entering loop
		fdtmc.createTransition(source, opStart, "", loopProbability); // entering loop
		fdtmc.createTransition(opEnd, opStart, "", loopProbability); // restarting loop
		fdtmc.createTransition(opEnd, target, "", "1 - " + loopProbability); // leaving loop

		transformLoopOperand (fdtmc, name, operand, opStart, opEnd, error, currentRdgNode);
		return target;
	}

	/**
	 * Recursively augments the fdtmc with $fragments information
	 * @param fdtmc
	 * @param fragment: a fragment of type alternative
	 * @param source: the fDTMC node that triggers or not the Fragment
	 * @param target: the fDTMC node that the fragment should return to
	 * @param error: the error state where message transmission failure should be transited to
	 * @return the $target itself, the point in the fDTMC where the execution or not of this $fragment will transit to
	 * @throws InvalidNodeClassException
	 * @throws InvalidNumberOfOperandsException
	 */
	private State transformAltFragment(FDTMC fdtmc, Fragment fragment, State source, State target, State error, RDGNode currentRdgNode) throws InvalidNodeClassException, InvalidNumberOfOperandsException, InvalidNodeType {
		ArrayList<Node> operands = fragment.getNodes();
		String name;

		String opElse = "";
		for(Node node : operands) {
			if (!node.getClass().equals(Operand.class)) throw new InvalidNodeClassException("An Alt Fragment can only have Operand objects as Nodes!");
			Operand operand = (Operand)node; // to facilitate the nodes use

			String guard = operand.getGuard();

			name = guard.equals("else") ? fragment.getName() + guard : guard;
			State opStart = fdtmc.createState("initial" + name);
			State opEnd = fdtmc.createState("end" + name);
			State opError = fdtmc.createState("error" + name);

			if (!operand.getGuard().equals("else")) {
			    // TODO Think about these feature-presence transitions...
			    // They do not adapt to the phi-functions
				opElse = opElse + "f" + guard + " - ";
				fdtmc.createTransition(source, opStart, guard, "f" + name); // entering operand
			} else {
				opElse = opElse.substring(0, opElse.length() - 3);
				fdtmc.createTransition(source, opStart, guard, "1 - " + opElse);
			}

			fdtmc.createTransition(opStart, opEnd, "", name); // interface transitions
			fdtmc.createTransition(opStart, opError, "", "1 - "+name); // interface transitions
			fdtmc.createTransition(opEnd, target, "", "1.0"); // leaving operand

//			creates FDTMC for loop content
			RDGNode altNode = transformOperand (name, guard, operand);
			currentRdgNode.addDependency(altNode);
		}
		return target;
	}

	/**
	 * Recursively augments the fdtmc with $fragments information
	 * @param fdtmc
	 * @param fragment: a fragment of type optional
	 * @param source: the fDTMC node that triggers or not the Fragment
	 * @param target: the fDTMC node that the fragment should return to
	 * @param error: the error state where message transmission failure should be transited to
	 * @return the $target itself, the point in the fDTMC where the execution or not of this $fragment will transit to
	 * @throws InvalidNumberOfOperandsException
	 * @throws InvalidNodeClassException
	 */
	private State transformOptFragment(FDTMC fdtmc, Fragment fragment, State source, State target, State error, RDGNode currentRdgNode) throws InvalidNumberOfOperandsException, InvalidNodeClassException, InvalidNodeType {
		if (fragment.getNodes().size() > 1) throw new InvalidNumberOfOperandsException("An Opt fragment can only have 1 operand!");

		Operand operand = (Operand)fragment.getNodes().get(0);
		String name = operand.getGuard();
		String guard = operand.getGuard();
		State featureStart = fdtmc.createState("initial" + name);
		State featureEnd = fdtmc.createState("end" + name);
		State featureError = fdtmc.createState("error" + name);

		// TODO: Check if the commented lines are being missed.
		//fdtmc.createTransition(source, target, operand.getGuard(), "1 - f" + operand.getGuard()); // not entering opt
        //fdtmc.createTransition(source, featureStart, operand.getGuard(), "f" + operand.getGuard().toString()); // into Feature
        fdtmc.createTransition(source, featureStart, name, "1.0"); // into Feature
        // When the feature is not present, its reliability will be taken as 1.
		fdtmc.createTransition(featureStart, featureEnd, "", name); // interface transitions
		fdtmc.createTransition(featureStart, featureError, "", "1 - "+name); // interface transitions
		fdtmc.createTransition(featureEnd, target, "", "1.0"); // leaving Feature

//		creates FDTMC for opt content
		RDGNode optNode = transformOperand(name, guard, operand);
		currentRdgNode.addDependency(optNode);

		return target;
	}

	/**
	 * Recursively augments the fdtmc with $fragments information
	 * @param fdtmc
	 * @param fragment: a fragment of type parallel
	 * @param source: the fDTMC node that triggers or not the Fragment
	 * @param target: the fDTMC node that the fragment should return to
	 * @param error: the error state where message transmission failure should be transited to
	 * @return the $target itself, the point in the fDTMC where the execution or not of this $fragment will transit to
	 */
	private State transformParallelFragment(FDTMC fdtmc, Fragment fragment, State source, State target, State error, RDGNode currentRdgNode) throws InvalidNodeClassException, InvalidNumberOfOperandsException, InvalidNodeType {
		ArrayList<Node> operands = fragment.getNodes();
		String fragName, opName;
		int n = operands.size(), opNum;
		float val = 1/(float)n;

		fragName = !fragment.getName().isEmpty() ? fragment.getName() : "Par" + ++parNum;
		opNum = 0;
		for(Node node : operands) {
			if (!node.getClass().equals(Operand.class)) throw new InvalidNodeClassException("A Par Fragment can only have Operand objects as Nodes!");
			Operand operand = (Operand)node; // to facilitate the nodes use
			opName = fragName + "-Op" + ++opNum;

			State opStart = fdtmc.createState("initial" + opName);
			State opEnd = fdtmc.createState("end" + opName);
			State opError = fdtmc.createState("error" + opName);

			fdtmc.createTransition(source, opStart, "", Float.toString(val)); // entering operand
			fdtmc.createTransition(opStart, opEnd, "", opName); // interface transitions
			fdtmc.createTransition(opStart, opError, "", "1 - "+opName); // interface transitions
			fdtmc.createTransition(opEnd, target, "", "1.0"); // leaving operand

//			creates FDTMC for loop content
			RDGNode fragmentNode = transformOperand(opName, "true", operand);
			currentRdgNode.addDependency(fragmentNode);
		}
		return target;
	}

	private RDGNode transformOperand (String name, String presenceCondition, Operand operand) throws InvalidNumberOfOperandsException, InvalidNodeClassException, InvalidNodeType {
		boolean isNew = checkNew (name);

		countCallsModel (name);

		if (!isNew) { /* Fragmento ja foi modelado */
			return RDGNode.getById(name);
		}

		FDTMC fdtmc = new FDTMC();

		fdtmc.setVariableName("s" + name);
		fdtmcByName.put(name, fdtmc);

		State init = fdtmc.createState("initial");
		State error = fdtmc.createState("error");
		State source = init;

		RDGNode rdgNode = new RDGNode(name, presenceCondition, fdtmc);
		transformFDTMCNodes(fdtmc, operand.getNodes(), source, error, rdgNode);
		System.out.println(fdtmc.toString());

		return rdgNode;
	}

	private void transformLoopOperand (FDTMC fdtmc, String name, Operand operand, State source, State target, State error, RDGNode currentRdgNode) throws InvalidNumberOfOperandsException, InvalidNodeClassException, InvalidNodeType {

		transformFDTMCNodes(fdtmc, operand.getNodes(), source, target, error, currentRdgNode);
		System.out.println(fdtmc.toString());
	}

	/**
	 * transformSingleAD auxiliary method
	 * @param fdtmc
	 * @param sourceState
	 * @param adEdge
	 */
	private void transformPath(FDTMC fdtmc, State sourceState, State errorState, Edge adEdge) {
		Activity targetAct = adEdge.getTarget();
		Activity sourceAct = adEdge.getSource();
		State targetState;

		String sourceActivitySD = sourceAct.getSd() != null ? sourceAct.getSd().getName() : "";

		if (sourceAct.getType().equals(ActivityType.initialNode)) {
			for (Edge e : targetAct.getOutgoing()) {
				transformPath(fdtmc, sourceState, errorState, e);
			}
		} else if (sourceAct.getType().equals(ActivityType.call)) {
			stateByActID.put(sourceAct.getId(), sourceState); // insere source no hashmap
			targetState = stateByActID.get(targetAct.getId()); // verifica se target esta no hashmap

			if (targetState == null) { // atividade target nao foi criada
				if (targetAct.getType().equals(ActivityType.finalNode)) {
					targetState = fdtmc.createState("success");
					stateByActID.put(targetAct.getId(), targetState);
					fdtmc.createTransition(targetState, targetState, "", "1.0");
				}
				else targetState = fdtmc.createState();

                fdtmc.createTransition(sourceState, targetState, sourceAct.getName(), sourceActivitySD);
                fdtmc.createTransition(sourceState, errorState, "!"+sourceAct.getName(), "1 - "+sourceActivitySD);

				/* continue path */
				for (Edge e : targetAct.getOutgoing()) {
					transformPath(fdtmc, targetState, errorState, e);
				}
			} else { // atividade target ja foi criada
				fdtmc.createTransition(sourceState, targetState, sourceAct.getName(), sourceActivitySD);
                fdtmc.createTransition(sourceState, errorState, "!"+sourceAct.getName(), "1 - "+sourceActivitySD);
				/* end path */
			}
		} else if (sourceAct.getType().equals(ActivityType.decision)) {
			stateByActID.put(sourceAct.getId(), sourceState); // insere source no hashmap
			targetState = stateByActID.get(targetAct.getId()); // verifica se target esta no hashmap

			if (targetState == null) { // atividade target nao foi criada
				if (targetAct.getType().equals(ActivityType.finalNode)) {
					targetState = fdtmc.createState("success");
					stateByActID.put(targetAct.getId(), targetState);
					fdtmc.createTransition(targetState, targetState, "", "1.0");
				}
				else targetState = fdtmc.createState();

				// Assuming equiprobable choice at decision node.
				// TODO Use annotated probabilities for AD decision nodes.
				fdtmc.createTransition(sourceState, targetState, "", Double.toString(1.0/sourceAct.getOutgoingCount()));

				/* continue path */
				for (Edge e : targetAct.getOutgoing()) {
					transformPath(fdtmc, targetState, errorState, e);
				}
			} else { // atividade target ja foi criada
                // Assuming equiprobable choice at decision node.
				fdtmc.createTransition(sourceState, targetState, "", Double.toString(1.0/sourceAct.getOutgoingCount()));
				/* end path */
			}
		} else if (sourceAct.getType().equals(ActivityType.merge)) {
			stateByActID.put(sourceAct.getId(), sourceState); // insere source no hashmap
			targetState = stateByActID.get(targetAct.getId()); // verifica se target esta no hashmap

			if (targetState == null) { // atividade target nao foi criada
				if (targetAct.getType().equals(ActivityType.finalNode)) {
					targetState = fdtmc.createState("final");
					stateByActID.put(targetAct.getId(), targetState);
					fdtmc.createTransition(targetState, targetState, "", "1.0");
				}
				else targetState = fdtmc.createState();

				fdtmc.createTransition(sourceState, targetState, sourceAct.getName(), "1.0");

				/* continue path */
				for (Edge e : targetAct.getOutgoing()) {
					transformPath(fdtmc, targetState, errorState, e);
				}
			} else { // atividade target ja foi criada
				fdtmc.createTransition(sourceState, targetState, sourceAct.getName(), "1.0");
				/* end path */
			}
		}
	}

	// Effort measurement methods

	public boolean checkNew (String name) {
		if (fdtmcByName.get(name) != null) {
			return false;
		}
		return true;
	}

	public void countCallsModel (String name) {
		if (fdtmcByName.get(name) != null) {
			nCallsByName.put(name, nCallsByName.get(name) + 1);
			return;
		}
		nCallsByName.put(name, 1);
	}

	public void measureSizeModel (FDTMC fdtmc) {
		Integer nStates, nTrans = 0;

		nStates = fdtmc.getStates().size();
		Set<State> states = fdtmc.getTransitions().keySet();
		Iterator <State> itStates = states.iterator();
		while (itStates.hasNext()) {
			State temp = itStates.next();
			if (fdtmc.getTransitions().get(temp) != null)
				nTrans += fdtmc.getTransitions().get(temp).size();
		}
		System.out.println("Model Size: " + nStates + " states; " + nTrans + " transitions.");
	}

	public void printNumberOfCalls (String name) {
		int num = nCallsByName.get(name);
		System.out.println(num);
	}

	// Getters and Setters

	public HashMap<String, FDTMC> getFdtmcByName() {
		return fdtmcByName;
	}

	public HashMap<String, Integer> getnCallsByName() {
		return nCallsByName;
	}
}