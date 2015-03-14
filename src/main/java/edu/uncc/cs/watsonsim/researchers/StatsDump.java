package edu.uncc.cs.watsonsim.researchers;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;

import edu.uncc.cs.watsonsim.Answer;
import edu.uncc.cs.watsonsim.Database;
import edu.uncc.cs.watsonsim.Question;
import edu.uncc.cs.watsonsim.Score;


/** Pipe Answer scores to an ARFF file for Weka */
public class StatsDump extends Researcher {
	
	private Database db = new Database();
	private PreparedStatement add_run = db.prep(
			"INSERT INTO results_runs(id) VALUES (?);");
	private PreparedStatement add_question = db.prep(
			"INSERT INTO results_questions(run_id, question) VALUES (?, ?) "
			+ "RETURNING id;");
	private PreparedStatement add_answer = db.prep(
			"INSERT INTO results_answers(results_questions_id, candidate_text)"
			+ " VALUES (?, ?) RETURNING id;");
	private PreparedStatement add_score = db.prep(
			"INSERT INTO results_scores(results_answers_id, key, value)"
			+ " VALUES (?, ?, ?);");
	
	private Timestamp run_id;
	private boolean broken = false;
	
	/**
	 * Start a new run in the reports tables.
	 */
	public StatsDump(Timestamp run_id) {
		this.run_id = run_id;
		try {
			add_run.setTimestamp(1, run_id);
			add_run.execute();
		} catch (SQLException e) {
			// If the run exists already, it will fail. No problem.
			// We will just append to it.
		}
	}
	
	/**
	 * Store a question with its answers and scores in the reports tables.
	 */
	@Override
	public synchronized void question(Question q) {
		if (!broken) {
			try {
				add_question.setTimestamp(1, run_id);
				add_question.setString(2, q.getRaw_text());
				long question_id = db.then(add_question).getLong(1);
				
				for (Answer a : q) {
					add_answer.setLong(1, question_id);
					add_answer.setString(2, a.candidate_text);
					long answer_id = db.then(add_answer).getLong(1);
					
					for (Map.Entry<String, Double> e : Score.asMap(a.scores).entrySet()) {
						add_score.setLong(1, answer_id);
						add_score.setString(2, e.getKey());
						add_score.setDouble(3, e.getValue());
						add_score.addBatch();
					}
				}
				add_score.executeBatch();
			} catch (SQLException e) {
				/* This is also triggered when the user enters a question
				 * such as via the command line, because it violates a foreign
				 * key constraint on questions.question; this constraint is
				 * useful because it allows us to easily know the category of
				 * a question, so I hesitate to remove it.
				 * TL;DR: User entered questions are not recorded.
				 */
				broken=true;
			}
		}
	}

}
