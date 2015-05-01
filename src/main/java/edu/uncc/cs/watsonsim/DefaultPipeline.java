package edu.uncc.cs.watsonsim;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import edu.uncc.cs.watsonsim.researchers.*;
import edu.uncc.cs.watsonsim.scorers.*;
import edu.uncc.cs.watsonsim.search.*;

/** The standard Question Analysis pipeline.
 * 
 * The pipeline is central to the DeepQA framework.
 * It consists of {@link Searcher}s, {@link Researcher}s, {@link Scorer}s, and
 * a {@link Learner}.<p>
 * 
 * Each step in the pipeline takes and possibly transforms a {@link Question}.
 * {@link Question}s aggregate {@link Answer}s, and a correct {@link Answer} (if it is
 *     known).
 * {@link Answer}s aggregate scores (which are primitive doubles) and
 *     {@link Passage}s, and contain a candidate text.
 * {@link Passage}s aggregate more scores, and provide some utilities for
 *     processing the text they contain.<p>
 * 
 * A {@link Searcher} takes the {@link Question}, runs generic transformations
 *     on its text and runs a search engine on it. The Passages it creates are
 *     promoted into {@link Answer}s, where the Passage title is the candidate
 *     {@link Answer} text and each {@link Answer} has one Passage. The passage
 *     Searchers do the same but are optimized for taking {@link Answer}s and
 *     finding supporting evidence as Passages. In that case, the resulting
 *     Passages are not promoted.<p>
 * 
 * A {@link Researcher} takes a {@link Question} and performs a transformation
 *     on it. There is no contract regarding what it can do to the
 *     {@link Question}, so they can't be safely run in parallel and the order
 *     of execution matters. Read the source for an idea of the intended order.
 *     <p>
 * 
 * A {@link Scorer} takes a {@link Question} and generates scores for either
 *     {@link Answer}s or {@link Passage}s (inheriting from
 *     {@link AnswerScorer} or {@link PassageScorer} respectively.)<p>
 *
 */
public class DefaultPipeline {
	
	private final Timestamp run_start;
	private final Searcher[] searchers;
	private final Researcher early_researchers;
	private final Scorer[] scorers;
	private final Researcher late_researchers;
	
	/**
	 * Start a pipeline with a new timestamp for the statistics dump
	 */
	public DefaultPipeline() {
		this(System.currentTimeMillis());
	}
	
	/**
	 * Start a pipeline with an existing timestamp
	 * @param millis Millis since the Unix epoch, as in currentTimeMillis()
	 */
	public DefaultPipeline(long millis) {
		Environment env = new Environment();
		run_start = new Timestamp(millis);
		
		/*
		 * Create the pipeline
		 */
		searchers = new Searcher[]{
			new LuceneSearcher(env),
			new IndriSearcher(env, false),
			// You may want to cache Bing results
			// new BingSearcher(config),
			new CachingSearcher(env, new BingSearcher(env), "bing"),
			new Anagrams(env)
		};
		early_researchers = Researcher.pipe(
			//new RedirectSynonyms(env),
			new HyphenTrimmer(),
			new StrictFilters(),
			new MergeByText(env),
			new MergeAnswers(),
			//new ChangeFitbAnswerToContentsOfBlanks(),
			new PassageRetrieval(env,
					new LucenePassageSearcher(env),
					new IndriSearcher(env, true)
					//new CachingSearcher(new BingSearcher(env), "bing"),
				),
			new MergeByCommonSupport(),
			new PersonRecognition(),
			new TagLAT(env),
			new MergeByCommonSupport()
		);
		scorers = new Scorer[]{
			new AnswerLength(),
			new AnswerPOS(),
			new CommonConstituents(),
			new Correct(env),
			new DateMatches(),
			new LATCheck(env),
			new LuceneEcho(),
			new NGram(),
			new PassageTermMatch(),
			new PassageCount(),
			new PassageQuestionLengthRatio(),
			new QPKeywordMatch(),
			new QAKeywordMatch(),
			new SkipBigram(),
			new TopPOS(),
			new WordProximity(),
			new WPPageViews(env)
			//new RandomIndexingCosineSimilarity(),
			//new DistSemCosQAScore(),
			//new DistSemCosQPScore(),
		};
		late_researchers = Researcher.pipe(
			new Normalize(),
			new WekaTee(run_start),
			new CombineScores(),
			new StatsDump(run_start, env)
		);
	}
	
	public List<Answer> ask(String qtext) {
	    return ask(new Question(qtext));
	}
	
    /** Run the full standard pipeline */
	public List<Answer> ask(Question question) {
		// Query every engine
		Logger l = Logger.getLogger(this.getClass());
		
		l.info("Generating candidate answers..");
		List<Answer> answers = new ArrayList<>();
		for (Searcher s: searchers)
			for (Passage p : s.query(question))
				answers.add(new Answer(p));
		l.info("Generated " + answers.size() + " candidate answers.");
		
		
		answers = early_researchers.pull(question, answers);
    	
    	l.info("Scoring supporting evidence..");
        for (Scorer s: scorers)
        	s.scoreQuestion(question, answers);
        
        l.info("Computing confidence..");
        /*
        List<Answer> answers_updated = new ArrayList<>();
        for(int x=0;x<answers.size();x++) {
        	Answer ans = answers.get(x);
        	String text = ans.text;
        	//System.out.println(text);
        	String[] answer_array = text.split(" ");
        	int answer_array_length = answer_array.length;
        	
        	
        	
        	for (int j = 0; j < answer_array_length; j++) {
				for (int i = answer_array_length - 1; i >= j; i--) {
					StringBuilder sb = new StringBuilder();
					for (int k = j; k <= i; k++) {
						// System.out.println("i=" + i + ", j=" + j + ", k");
						sb.append(answer_array[k]);
						if (k != i)
							sb.append(" ");
					}
					if (sb.toString() != "" && question.text.toLowerCase().contains(sb.toString().toLowerCase())) {
						text = text.toString().replace(sb.toString(), "");
					text = text.trim().replaceAll(" +", " ");
					text = text.replaceAll("^([^a-z|A-Z|0-9])( )*", "");
                    text = text.replaceAll("()*([^a-z|A-Z|0-9])$", "").trim();
						answer_array = text.split(" ");
						answer_array_length = answer_array.length;
						i = answer_array_length - 1;
						j = 0;
					}
				}
			}
        	answers_updated.add( ans.withText(text));
        }*/
        
        //for(int i=0;i<answers.size();i++)
        //	System.out.println(answers.get(i).text+"//"+answers_updated.get(i).text);
        
        
        //answers = late_researchers.pull(question, answers_updated); 
        
        answers = late_researchers.pull(question, answers);
        return answers;
    }
}
