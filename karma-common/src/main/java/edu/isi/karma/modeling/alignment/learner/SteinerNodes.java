/*******************************************************************************
 * Copyright 2012 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/

package edu.isi.karma.modeling.alignment.learner;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import edu.isi.karma.modeling.ModelingConfiguration;
import edu.isi.karma.rep.alignment.ColumnNode;
import edu.isi.karma.rep.alignment.InternalNode;
import edu.isi.karma.rep.alignment.Node;
import edu.isi.karma.util.RandomGUID;

public class SteinerNodes implements Comparable<SteinerNodes> {

	private static final double MIN_CONFIDENCE = 1E-6;
	
	private Set<Node> nodes;
	private Map<ColumnNode, ColumnNode> mappingToSourceColumns;
	private Confidence confidence;
	private Coherence coherence;
//	private int frequency;
	private double score;
	private int semanticTypesCount;
	private int nonModelNodesCount; // nodes that do not belong to any pattern

	
	public SteinerNodes() {
		this.nodes = new HashSet<Node>();
		this.mappingToSourceColumns = new HashMap<ColumnNode, ColumnNode>();
		this.semanticTypesCount = 0;
		this.confidence = new Confidence();
		this.coherence = new Coherence();
		this.nonModelNodesCount = 0;
//		this.frequency = 0;
		this.score = 0.0;
	}
	
	public SteinerNodes(SteinerNodes steinerNodes) {
		this.nodes = new HashSet<Node>(steinerNodes.getNodes());
		this.mappingToSourceColumns = new HashMap<ColumnNode, ColumnNode>(steinerNodes.getMappingToSourceColumns());
		this.confidence = new Confidence(steinerNodes.getConfidence());
		this.coherence = new Coherence(steinerNodes.getCoherence());
//		this.frequency = steinerNodes.getFrequency();
		this.semanticTypesCount = steinerNodes.getSemanticTypesCount();
		this.nonModelNodesCount = steinerNodes.getNonModelNodesCount();
		this.score = steinerNodes.getScore();
	}
	
	public Set<Node> getNodes() {
		return Collections.unmodifiableSet(this.nodes);
	}
	
	public Map<ColumnNode, ColumnNode> getMappingToSourceColumns() {
		return mappingToSourceColumns;
	}

	public int getSemanticTypesCount() {
		return semanticTypesCount;
	}

	public boolean addNodes(ColumnNode sourceColumn, InternalNode n1, ColumnNode n2, double confidence) {
		
		if (this.nodes.contains(n1) && this.nodes.contains(n2))
			return false;
		
		this.semanticTypesCount ++;
		
		if (!this.nodes.contains(n1)) {
			this.nodes.add(n1);
			if (n1.getModelIds() == null || n1.getModelIds().isEmpty())
				this.nonModelNodesCount ++;
		}
		if (!this.nodes.contains(n2)) {
			this.nodes.add(n2);
			this.mappingToSourceColumns.put(n2, sourceColumn);
			if (n2.getModelIds() == null || n2.getModelIds().isEmpty())
				this.nonModelNodesCount ++;
		}

		if (confidence <= 0 || confidence > 1)
			confidence = MIN_CONFIDENCE;
		
		this.confidence.addValue(confidence);
		
//		this.frequency += n1.getModelIds() == null ? 0 : n1.getModelIds().size();
//		this.frequency += n2.getModelIds() == null ? 0 : n2.getModelIds().size();
		
		this.computeCoherence();
		
		this.computeScore();
		
		return true;
		
	}
	
	public Confidence getConfidence() {
		return this.confidence;
	}
	
	public int getNodesCount() {
		return this.nodes.size();
	}
	
	public int getNonModelNodesCount() {
		return this.nonModelNodesCount;
	}
	
	public double getScore() {
		return this.score;
	}
	
	public Coherence getCoherence() {
		return this.coherence;
	}
	
//	public int getFrequency() {
//		return frequency;
//	}
	
//	private int computeFrequency() {
//		int frequency = 0;
//		for (Node n : this.nodes)
//			frequency += n.getPatternIds().size();
//		return frequency;
//	}

//	private double computeConfidenceValue() {
//		
//		if (this.confidenceList.size() == 1)
//			return 1e-10;
//		
//		double confidence = 1.0;
//		
//		for (double d : this.confidenceList) {
//			if (d == 0)
//				confidence *= 1e-10;
//			else
//				confidence *= d;
//		}
//		
//		return confidence;
//	}

	
	private void computeCoherence() {
		
		if (nodes == null || nodes.size() == 0)
			return;

		Map<String, Integer> patternSize = new HashMap<String, Integer>();
		Map<String, String> patternGuid = new HashMap<String, String>();
		int guidSize = new RandomGUID().toString().length();
		
		for (Node n : nodes) {
			for (String p : n.getModelIds()) {
				
				Integer size = patternSize.get(p);
				if (size == null) 
					patternSize.put(p, 1);
				else
					patternSize.put(p, ++size);
				
				if (!patternGuid.containsKey(p)) {
					String guid = new RandomGUID().toString();
					patternGuid.put(p, guid);
				}
			}
		}
		
		// find the maximum pattern size
		int maxPatternSize = 0;
		for (Entry<String, Integer> entry : patternSize.entrySet()) {
			if (entry.getValue().intValue() > maxPatternSize)
				maxPatternSize = entry.getValue().intValue();
		}
		
		List<String> listOfNodesLargestPatterns = new ArrayList<String>();
		
		for (Node n : nodes) {
			List<String> patternIds = new ArrayList<String>(n.getModelIds());
			Collections.sort(patternIds);
			
			String[] nodeMaxPatterns = new String[maxPatternSize];
			Arrays.fill(nodeMaxPatterns, "");
			
			for (String p : patternIds) {
				int size = patternSize.get(p).intValue();
				nodeMaxPatterns[size - 1] += patternGuid.get(p);
			}
			for (int i = maxPatternSize - 1; i >= 0; i--) {
				if (nodeMaxPatterns[i] != null && nodeMaxPatterns[i].trim().length() > 0) {
					listOfNodesLargestPatterns.add(nodeMaxPatterns[i]);
					break;
				}
			}
		}	
		
		Function<String, String> stringEqualiy = new Function<String, String>() {
			  @Override public String apply(final String s) {
			    return s;
			  }
			};
				
		Multimap<String, String> index =
			Multimaps.index(listOfNodesLargestPatterns, stringEqualiy);
		
		int x, y;
		this.coherence = new Coherence(this.getNodesCount());
		for (String s : index.keySet()) {
			if (s.trim().length() == 0)
				continue;
			x = index.get(s).size();
			y = x > 0 ? index.get(s).iterator().next().length() / guidSize : 0; 
			CoherenceItem ci = new CoherenceItem(x, y);
			this.coherence.addItem(ci);
		}
		
	}
	
	
	private double getSizeReduction() {
		
		int minSize = this.semanticTypesCount;
		int maxSize = this.semanticTypesCount * 2;
		
		//feature scaling: (x - min) / (max - min)
		// here: x: reduction in size --- min reduction: 0 --- max reduction: maxSize - minSize 
		return (double)(maxSize - this.getNodesCount()) / 
				(double)(maxSize - minSize);
		
	}

	
//	private double getHarmonicMean(double[] input) {
//		
//		double result = 0.0;
//		if (input == null)
//			return result;
//		
//		double min = 1E-6;
//		double sum = 0.0;
//		for (double d : input) {
//			if (d <= 0.0) d = min;
//			sum += 1.0 / d;
//		}
//		
//		if (sum == 0.0)
//			return result;
//		
//		result = (double) input.length / sum;
//		return result;
//		
//	}
	
	private double getArithmeticMean(double[] input) {
		
		double result = 0.0;
		if (input == null)
			return 0.0;
		
		double sum = 0.0;
		for (double d : input) {
			if (d < 0.0) d = 0.0;
			sum += d;
		}
		
		result = sum / (double)input.length;
		return result;
		
	}
	
	private void computeScore() {
		
		double confidence = this.confidence.getConfidenceValue();
		double sizeReduction = this.getSizeReduction();
		double coherence = this.coherence.getCoherenceValue();
		//int frequency = this.getFrequency();
		
		double alpha = ModelingConfiguration.getScoringConfidenceCoefficient();
		double beta = ModelingConfiguration.getScoringCoherenceSCoefficient();
		double gamma = ModelingConfiguration.getScoringSizeCoefficient();
//		
//		this.score = alpha * coherence + 
//				beta * distanceToMaxSize + 
//				gamma * confidence;
		
		double[] measures = new double[3];
		measures[0] = alpha * confidence;
		measures[1] = beta * coherence;
		measures[2] = gamma * sizeReduction;
//		this.score = sizeReduction;
//		this.score = coherence;
//		this.score = confidence;
//		this.score = getHarmonicMean(measures);
		this.score = getArithmeticMean(measures);
	}

	@Override
	public int compareTo(SteinerNodes target) {
		
		double score1 = this.getScore();
		double score2 = target.getScore();
		
		if (score1 < score2)
			return 1;
		else if (score1 > score2)
			return -1;
		else return 0;
	}
	
	
	private static double roundTwoDecimals(double d) {
        DecimalFormat twoDForm = new DecimalFormat("#.##");
        return Double.valueOf(twoDForm.format(d));
	}	

	public String getScoreDetailsString() {
//		this.computeCoherenceList();
		StringBuffer sb = new StringBuffer();
		
//		if (this.nodes != null)
//		for (Node n : this.nodes) {
//			if (n instanceof InternalNode)
//				sb.append(n.getLocalId());
//			else {
//				if (mappingToSourceColumns.containsKey((ColumnNode)n))
//					sb.append(mappingToSourceColumns.get((ColumnNode)n).getColumnName() );
//				else
//					sb.append( ((ColumnNode)n).getColumnName() );
//			}
//			sb.append("|");
//		}
//		sb.append("\n");

		sb.append("\n");
		sb.append("coherence list: ");
		for (CoherenceItem ci : this.coherence.getItems()) {
			sb.append("(" + ci.getX() + "," + ci.getY() + ")");
		}
//		sb.append("\n");
		sb.append("--- coherence value: " + this.coherence.getCoherenceValue());
		sb.append("\n");
		sb.append("size: " + this.getNodesCount() + ", max size: " + (this.semanticTypesCount * 2) + "---" + 
				"size reduction: " +  roundTwoDecimals(this.getSizeReduction()) );
		sb.append("\n");
		sb.append("confidence list: (");
		for (Double cf : this.confidence.getValues()) {
			if (cf != null)
				sb.append( roundTwoDecimals(cf.doubleValue()) + ",");
		}
		sb.append(") --- ");
		sb.append("confidence: " + roundTwoDecimals(this.confidence.getConfidenceValue()));
		sb.append("\n");
//		sb.append("total number of patterns: " + this.frequency);
//		sb.append("\n");
		sb.append("final score: " + roundTwoDecimals(this.getScore()) + " - [arithmetic mean]");
		sb.append("\n");
		return sb.toString();
	}
		
}
