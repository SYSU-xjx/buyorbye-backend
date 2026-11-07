-- Query indexes only. Legacy weekly snapshot tables and V1/V2 remain untouched.
CREATE INDEX idx_proposal_ranking ON purchase_proposals (proposal_status, participation_type, decision_result, settled_at, creator_user_id);
CREATE INDEX idx_vote_period ON decision_votes (cast_at, voter_user_id, proposal_id);
