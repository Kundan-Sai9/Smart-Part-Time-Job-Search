import os
from fastapi import HTTPException
from api.schemas import LlmRecommendationRequest, ProfileRequest
from core import state

def llm_job_recommendations(req: LlmRecommendationRequest):
    top_k = max(1, min(req.top_k, 10))

    if state.LANGCHAIN_LLM_AVAILABLE and os.environ.get('COHERE_API_KEY'):
        try:
            prompt = state.ChatPromptTemplate.from_template(
                """
You are a job matching expert.
Given this user profile:
{profile}

And these job listings:
{jobs}

Return ONLY valid JSON in this format:
[
  {
    "jobId": 123,
    "matchScore": 85,
    "reasons": ["reason 1", "reason 2"]
  }
]

Rules:
- Return top {top_k} jobs.
- matchScore must be integer 0-100.
- jobId must come from the provided jobs.
"""
            )

            llm = state.ChatCohere(model='command-r', temperature=0.2)
            parser = state.JsonOutputParser()
            chain = prompt | llm | parser
            parsed = chain.invoke({
                'profile': req.user_profile,
                'jobs': req.jobs,
                'top_k': top_k
            })

            if not isinstance(parsed, list):
                raise ValueError('LLM returned non-list JSON')

            return {
                'recommendations': parsed[:top_k],
                'insights': [
                    'LangChain orchestration enabled',
                    'Cohere command-r generated structured ranking'
                ],
                'model_version': 'langchain-cohere-v1'
            }
        except Exception as e:
            print('LangChain LLM recommendations failed, using fallback:', e)

    profile_words = set([w.strip().lower() for w in req.user_profile.replace(',', ' ').split() if len(w.strip()) > 2])
    scored = []
    for j in req.jobs:
        job_id = j.get('id') if j.get('id') is not None else j.get('jobId')
        text = ' '.join([
            str(j.get('title', '')),
            str(j.get('description', '')),
            str(j.get('skills', '')),
            str(j.get('company', '')),
            str(j.get('location', ''))
        ]).lower()
        overlap = len([w for w in profile_words if w in text])
        score = min(100, int(20 + overlap * 8))
        scored.append({
            'jobId': job_id,
            'matchScore': score,
            'reasons': [
                f'Keyword overlap score: {overlap}',
                'Fallback matching used (LangChain unavailable)'
            ]
        })

    scored = sorted(scored, key=lambda x: -x['matchScore'])[:top_k]
    return {
        'recommendations': scored,
        'insights': ['Fallback deterministic scoring used'],
        'model_version': 'fallback-matcher-v1'
    }

def score_profile(req: ProfileRequest):
    parts = []
    if req.bio and req.bio.strip():
        parts.append(req.bio.strip())
    if req.skills and req.skills.strip():
        parts.append(req.skills.strip())
    if req.experience and req.experience.strip():
        parts.append(req.experience.strip())
    
    profile_text = " ".join(parts).strip()
    
    # If no profile data or models not loaded, use a basic fallback
    if not profile_text or state.jobs is None or state.job_embeddings is None or state.embedder is None:
        score = 0
        if req.name and req.name.strip(): score += 10
        if req.bio and req.bio.strip(): score += 20
        if req.skills and req.skills.strip(): score += 20
        if req.experience and req.experience.strip(): score += 15
        
        return {
            'score': score, 
            'suggestion': 'Model not loaded or profile empty. Please add more details to your profile and ensure jobs are uploaded.',
            'model_version': 'profile-fallback'
        }

    # RAG/Similarity scoring
    import numpy as np
    from sklearn.metrics.pairwise import cosine_similarity
    import pandas as pd
    from collections import Counter
    
    user_emb = state.embedder.encode([profile_text], convert_to_numpy=True)
    sims = cosine_similarity(user_emb, state.job_embeddings)[0]
    
    # Get top 5 matching jobs
    top_k = 5
    top_idx = np.argsort(-sims)[:top_k]
    
    # Calculate score based on similarity (scale to 0-100)
    top_sims = sims[top_idx]
    avg_sim = float(np.mean(top_sims))
    
    # A good match in embedding space might be around 0.5 - 0.8. Let's scale it gently.
    # Score = min(100, max(0, avg_sim * 100 * 1.2))
    score = int(min(100, max(0, avg_sim * 120)))
    
    # Extract user skills
    user_skills_set = set()
    if req.skills:
        user_skills_set = set([s.strip().lower() for s in req.skills.split(',') if s.strip()])
    else:
        # try to extract from text
        user_skills_set = set([s.strip().lower() for s in profile_text.split() if len(s) > 2])
        
    # Analyze missing skills from top matching jobs
    missing_skills_counter = Counter()
    for idx in top_idx:
        row = state.jobs.iloc[idx]
        job_skills = str(row.get('Required Skills', '') or '')
        job_skills_set = set([s.strip().lower() for s in job_skills.split(',') if s.strip()])
        
        for skill in job_skills_set:
            if skill and skill not in user_skills_set:
                missing_skills_counter[skill] += 1
                
    # Generate suggestions
    if not missing_skills_counter:
        suggestion = 'Your profile strongly matches current job market demand! Keep your skills updated.'
    else:
        top_missing = [skill for skill, count in missing_skills_counter.most_common(3)]
        suggestion = f"To improve your matches, consider adding or learning these in-demand skills: {', '.join(top_missing)}."
        
    return {
        'score': score, 
        'suggestion': suggestion, 
        'model_version': 'profile-rag-v1'
    }


def analyze_profile_agent(req: ProfileRequest):
    profile_text = f"Name: {req.name}\nBio: {req.bio}\nSkills: {req.skills}\nExperience: {req.experience}\nRole: {req.role}"

    if state.LANGCHAIN_LLM_AVAILABLE and os.environ.get('COHERE_API_KEY'):
        try:
            @state.tool
            def check_skill_demand(skill: str) -> str:
                """Check whether a skill is currently in-demand in uploaded job listings."""
                if state.jobs is None or len(state.jobs) == 0:
                    return 'No jobs loaded yet.'
                skill_l = skill.strip().lower()
                total = len(state.jobs)
                hits = 0
                for _, row in state.jobs.iterrows():
                    skills = str(row.get('Required Skills', '') or '').lower()
                    title = str(row.get('Job Title', '') or '').lower()
                    if skill_l and (skill_l in skills or skill_l in title):
                        hits += 1
                pct = round((hits / max(total, 1)) * 100, 1)
                return f"Skill '{skill}' appears in {hits}/{total} jobs ({pct}%)."

            @state.tool
            def suggest_certifications(role: str) -> str:
                """Suggest useful certifications for a given job role."""
                role_l = role.lower()
                if 'data' in role_l or 'ml' in role_l or 'ai' in role_l:
                    return 'Google Professional ML Engineer, AWS ML Specialty, Databricks ML Associate.'
                if 'cloud' in role_l or 'devops' in role_l:
                    return 'AWS Solutions Architect Associate, Azure Administrator Associate, CKAD.'
                if 'java' in role_l or 'backend' in role_l:
                    return 'Oracle Java SE Developer, AWS Developer Associate, Spring Professional.'
                if 'frontend' in role_l or 'react' in role_l:
                    return 'Meta Front-End Developer, JavaScript Algorithms and Data Structures.'
                return 'AWS Cloud Practitioner, Scrum Master, and role-specific vendor certifications.'

            llm = state.ChatCohere(model='command-r', temperature=0.2)
            tools = [check_skill_demand, suggest_certifications]

            react_prompt = state.PromptTemplate.from_template(
                """You are a career profile analysis agent.
You can use tools to check market demand and certification guidance.

Available tools:
{tools}

Tool names:
{tool_names}

Use this format:
Question: the input question
Thought: think about what to do
Action: one of [{tool_names}]
Action Input: input to the action
Observation: result of action
... (repeat Thought/Action/Action Input/Observation as needed)
Thought: I now know the final answer
Final Answer: concise actionable advice

Question: {input}
Thought:{agent_scratchpad}
"""
            )

            agent = state.create_react_agent(llm, tools, react_prompt)
            executor = state.AgentExecutor(
                agent=agent,
                tools=tools,
                verbose=False,
                handle_parsing_errors=True,
                max_iterations=4
            )

            user_question = (
                'Analyze this profile and provide: '
                '1) top improvement actions, '
                '2) in-demand skills to add, '
                '3) certifications to pursue. '\
                f'\n\nProfile:\n{profile_text}'
            )

            result = executor.invoke({'input': user_question})
            return {
                'analysis': result.get('output', ''),
                'model_version': 'langchain-agent-v1',
                'agentic': True
            }
        except Exception as e:
            print('Agentic profile analysis failed, using fallback:', e)

    score_resp = score_profile(req)
    fallback_analysis = (
        f"Profile score: {score_resp['score']}/100. "
        "Add measurable achievements to experience, include 8-12 core skills, "
        "and align your role focus with in-demand jobs in the platform."
    )
    return {
        'analysis': fallback_analysis,
        'model_version': 'fallback-profile-analyzer-v1',
        'agentic': False
    }
