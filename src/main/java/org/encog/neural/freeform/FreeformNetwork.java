package org.encog.neural.freeform;

import java.util.HashSet;
import java.util.Set;

import org.encog.Encog;
import org.encog.engine.network.activation.ActivationFunction;
import org.encog.engine.network.activation.ActivationTANH;
import org.encog.mathutil.randomize.ConsistentRandomizer;
import org.encog.ml.MLClassification;
import org.encog.ml.MLContext;
import org.encog.ml.MLEncodable;
import org.encog.ml.MLError;
import org.encog.ml.MLRegression;
import org.encog.ml.MLResettable;
import org.encog.ml.data.MLData;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.neural.freeform.basic.BasicActivationSummation;
import org.encog.neural.freeform.basic.BasicFreeformConnection;
import org.encog.neural.freeform.basic.BasicFreeformLayer;
import org.encog.neural.freeform.basic.BasicFreeformNeuron;
import org.encog.neural.freeform.task.ConnectionTask;
import org.encog.neural.freeform.task.NeuronTask;
import org.encog.neural.networks.BasicNetwork;
import org.encog.util.EngineArray;
import org.encog.util.simple.EncogUtility;

public class FreeformNetwork implements MLContext,
MLRegression, MLEncodable, MLResettable, MLClassification, MLError {
	
	private FreeformLayer inputLayer;
	private FreeformLayer outputLayer;

	public FreeformNetwork() {	
	}
	
	public FreeformLayer createInputLayer(final int neuronCount) {
		this.inputLayer = createLayer(neuronCount);
		return this.inputLayer;
	}
	
	public FreeformLayer createOutputLayer(final int neuronCount) {
		this.outputLayer = createLayer(neuronCount);
		return this.outputLayer;
	}
	
	public FreeformLayer createLayer(final int neuronCount)
	{
		FreeformLayer result = new BasicFreeformLayer();
		
		// Add the neurons for this layer
		for(int i=0;i<neuronCount;i++) {
			result.add(new BasicFreeformNeuron(null));
		}
		
		return result;
	}
		
	public FreeformNetwork(BasicNetwork network) {
		
		if( network.getLayerCount()<2 ) {
			throw new FreeformNeuralNetworkError("The BasicNetwork must have at least two layers to be converted.");
		}
		
		// handle each layer
		FreeformLayer previousLayer = null;
		FreeformLayer currentLayer;
		
		for(int currentLayerIndex = 0; currentLayerIndex<network.getLayerCount();currentLayerIndex++) {
			// create the layer
			currentLayer = new BasicFreeformLayer();
			
			// Is this the input layer?
			if( this.inputLayer == null) {
				this.inputLayer = currentLayer;
			}
			
			// Add the neurons for this layer
			for(int i=0;i<network.getLayerNeuronCount(currentLayerIndex);i++) {
				// obtain the summation object.
				BasicActivationSummation summation = null;
				
				if( previousLayer!=null ) {
					summation = new BasicActivationSummation(network.getActivation(currentLayerIndex));
				}
				
				// add the new neuron
				currentLayer.add(new BasicFreeformNeuron(summation));
			}
			
			// Fully connect this layer to previous
			if( previousLayer!=null ) {				
				connectLayersFromBasic(
						network, 
						currentLayerIndex-1,
						previousLayer,
						currentLayerIndex,
						currentLayer,
						currentLayerIndex,
						false);			
			}
			
			// Add the bias neuron
			// The bias is added after connections so it has no inputs
			if( network.isLayerBiased(currentLayerIndex) ) {
				BasicFreeformNeuron biasNeuron = new BasicFreeformNeuron(null);
				biasNeuron.setActivation(network.getLayerBiasActivation(currentLayerIndex));
				currentLayer.add(biasNeuron);
			}
						
			// update previous layer
			previousLayer = currentLayer;
			currentLayer = null;
		}
		
		// finally, set the output layer.
		this.outputLayer = previousLayer;
	}

	public void connectLayers(
			FreeformLayer source, 
			FreeformLayer target, 
			ActivationFunction theActivationFunction, 
			double biasActivation, 
			boolean isRecurrent) {
				
		// create bias, if requested
		if( biasActivation> Encog.DEFAULT_DOUBLE_EQUAL ) {
			BasicFreeformNeuron biasNeuron = new BasicFreeformNeuron(null);
			biasNeuron.setActivation(biasActivation);
			source.add(biasNeuron);
		}
		
		// create connections
		for(FreeformNeuron targetNeuron: target.getNeurons()) {
			// create the summation for the target
			InputSummation summation = new BasicActivationSummation(theActivationFunction);
			targetNeuron.setInputSummation(summation);
			
			// connect the source neurons to the target neuron
			for(FreeformNeuron sourceNeuron: source.getNeurons()) {				
				FreeformConnection connection = new BasicFreeformConnection(sourceNeuron,targetNeuron);
				sourceNeuron.addOutput(connection);
				targetNeuron.addInput(connection);
			}	
		}
	}
	
	public void connectLayers(FreeformLayer source, FreeformLayer target) {
		connectLayers(source,target,new ActivationTANH(),1.0,false);
	}
	
	public void ConnectLayers(FreeformLayer source, 
			FreeformLayer target, 
			ActivationFunction theActivationFunction) {
		connectLayers(source,target,theActivationFunction,1.0,false);
	}
	
	private void connectLayersFromBasic(BasicNetwork network, 
			int fromLayerIdx,
			FreeformLayer source,
			int sourceIdx,
			FreeformLayer target,
			int targetIdx,
			boolean isRecurrent) {
		
		for(int targetNeuronIdx = 0; targetNeuronIdx < target.size(); targetNeuronIdx++ ) {
			for(int sourceNeuronIdx = 0; sourceNeuronIdx < source.size(); sourceNeuronIdx++ ) {
				FreeformNeuron sourceNeuron = source.getNeurons().get(sourceNeuronIdx);
				FreeformNeuron targetNeuron = target.getNeurons().get(targetNeuronIdx);
				
				// neurons with no input (i.e. bias neurons)
				if( targetNeuron.getInputSummation()==null ) {
					continue;
				}
				
				FreeformConnection connection = new BasicFreeformConnection(sourceNeuron,targetNeuron);
				sourceNeuron.addOutput(connection);
				targetNeuron.addInput(connection);
				double weight = network.getWeight(fromLayerIdx, sourceNeuronIdx, targetNeuronIdx);
				connection.setWeight(weight);
			}	
		}		
	}

	@Override
	public int getInputCount() {
		return this.inputLayer.sizeNonBias();
	}

	@Override
	public int getOutputCount() {
		return this.outputLayer.sizeNonBias();
	}

	@Override
	public double calculateError(MLDataSet data) {
		return EncogUtility.calculateRegressionError(this, data);
	}

	@Override
	public int classify(MLData input) {
		final MLData output = compute(input);
		return EngineArray.maxIndex(output.getData());
	}

	@Override
	public void reset() {
		reset((int)(System.currentTimeMillis()%Integer.MAX_VALUE));		
	}

	@Override
	public void reset(int seed) {
		final ConsistentRandomizer randomizer = new ConsistentRandomizer(-1,1, seed);
		
		performConnectionTask(new ConnectionTask() {
			@Override
			public void task(FreeformConnection connection) {
				connection.setWeight(randomizer.nextDouble());
			}
		});
	}

	@Override
	public int encodedArrayLength() {
		

		return 0;
	}

	@Override
	public void encodeToArray(double[] encoded) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void decodeFromArray(double[] encoded) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public MLData compute(MLData input) {
		
		// Allocate result
		MLData result = new BasicMLData(this.outputLayer.size());
		
		// Copy the input
		for(int i=0;i<input.size();i++) {
			this.inputLayer.setActivation(i,input.getData(i));
		}
		
		// Request calculation of outputs
		for(int i=0;i<this.outputLayer.size();i++) {
			FreeformNeuron outputNeuron = this.outputLayer.getNeurons().get(i);
			outputNeuron.performCalculation();
			result.setData(i,outputNeuron.getActivation());
		}
		
		return result;
	}

	@Override
	public void clearContext() {
		// TODO Auto-generated method stub
		
	}
	
	public void performNeuronTask(NeuronTask task) {
		Set<FreeformNeuron> visited = new HashSet<FreeformNeuron>();
		
		for(FreeformNeuron neuron: this.outputLayer.getNeurons()) {
			performNeuronTask(visited,neuron,task);
		}
	}
	
	private void performNeuronTask(Set<FreeformNeuron> visited, FreeformNeuron parentNeuron, NeuronTask task) {
		visited.add(parentNeuron);
		task.task(parentNeuron);
		
		// does this neuron have any inputs?
		if( parentNeuron.getInputSummation()!=null ) { 
			// visit the inputs
			for(FreeformConnection connection : parentNeuron.getInputSummation().list() ) {
				FreeformNeuron neuron = connection.getSource();
				// have we already visited this neuron?
				if( !visited.contains(neuron) ) {
					performNeuronTask(visited,neuron,task);
				}
			}
		}
	}
	
	public void performConnectionTask(ConnectionTask task) {
		Set<FreeformNeuron> visited = new HashSet<FreeformNeuron>();
		
		for(FreeformNeuron neuron: this.outputLayer.getNeurons()) {
			performConnectionTask(visited,neuron,task);
		}
	}
	
	private void performConnectionTask(Set<FreeformNeuron> visited, FreeformNeuron parentNeuron, ConnectionTask task) {
		visited.add(parentNeuron);		
		
		// does this neuron have any inputs?
		if( parentNeuron.getInputSummation()!=null ) { 
			// visit the inputs
			for(FreeformConnection connection : parentNeuron.getInputSummation().list() ) {
				task.task(connection);
				FreeformNeuron neuron = connection.getSource();
				// have we already visited this neuron?
				if( !visited.contains(neuron) ) {
					performConnectionTask(visited,neuron,task);
				}
			}
		}
	}
	
	public void tempTrainingAllocate(final int neuronSize, final int connectionSize) {
		performNeuronTask(new NeuronTask()
		{
			@Override
			public void task(FreeformNeuron neuron) {
				neuron.allocateTempTraining(neuronSize);
				if( neuron.getInputSummation()!=null ) {
					for(FreeformConnection connection: neuron.getInputSummation().list()) {
						connection.allocateTempTraining(connectionSize);
					}
				}
			}
		});
	}

	public void tempTrainingClear() {
		performNeuronTask(new NeuronTask()
		{
			@Override
			public void task(FreeformNeuron neuron) {
				neuron.clearTempTraining();
				if( neuron.getInputSummation()!=null ) {
					for(FreeformConnection connection: neuron.getInputSummation().list()) {
						connection.clearTempTraining();
					}
				}
			}
		});
	}

	public FreeformLayer getOutputLayer() {
		return this.outputLayer;
	}

}
