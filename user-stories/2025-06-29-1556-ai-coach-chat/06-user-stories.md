# User Stories - Tiered AI Coach Chat System

## Epic: Tiered AI Coach Chat System

**Epic Description**: Implement a two-tier AI coaching system where free users access basic AI assistance with premade questions and limited conversations, while premium users unlock Dr. Lift - a personalized AI coach with plateau detection, injury awareness, and unlimited conversations.

**Business Value**: Creates compelling freemium upgrade path while providing immediate value to all users

**Technical Foundation**: Leverages existing Coach screen infrastructure, Firebase AI dependencies, workout analytics, and user data

---

## User Stories (Priority Order)

### 🔥 **Story 1: Onboarding Gate for AI Access**
**Priority**: P0 (Blocker)

**As a** new Liftrix user  
**I want** to complete the "getting started" onboarding including injury questions  
**So that** AI coaching can provide safe, personalized advice based on my limitations

**Acceptance Criteria**:
- [ ] User cannot access AI chat without completing onboarding
- [ ] Injury data from onboarding is stored and accessible to AI system
- [ ] Clear messaging explains why onboarding is required for AI access
- [ ] Existing users are prompted to complete injury questions if missing

**Technical Requirements**:
- Modify existing onboarding flow to include injury collection
- Create injury data model and storage
- Add gate logic in Coach screen navigation
- Update user profile to track onboarding completion status

---

### 🔥 **Story 2: Free Tier AI Assistant with Premade Questions**
**Priority**: P0 (MVP Core)

**As a** free tier user  
**I want** to access an AI assistant with premade questions and limited conversations  
**So that** I can get basic fitness guidance and experience AI coaching value

**Acceptance Criteria**:
- [ ] Premade question library with 10-15 common fitness questions
- [ ] Questions categorized: "Progress", "Programming", "Recovery", "Nutrition"
- [ ] Sample questions: "Why am I plateauing?", "How often should I train chest?", "What's causing my bench to stagnate?"
- [ ] 5-7 total conversation limit per account (not monthly reset)
- [ ] Clear conversation counter: "3 of 7 conversations remaining"
- [ ] AI provides helpful responses without personality features
- [ ] Upgrade prompts when conversation limit reached

**Technical Requirements**:
- Create premade question database with categories
- Implement conversation tracking per user account
- Basic AI service integration (simple prompt/response)
- Upgrade flow integration for conversation limits

---

### 🔥 **Story 3: Premium Dr. Lift AI Coach Personality**
**Priority**: P0 (MVP Core)

**As a** premium user  
**I want** to chat with Dr. Lift, an evidence-based AI coach  
**So that** I can get personalized, science-backed fitness guidance

**Acceptance Criteria**:
- [ ] Dr. Lift personality inspired by Jeff Nippard/Jeremy Ethier style
- [ ] Conversational tone: knowledgeable but approachable, not overly technical
- [ ] References evidence-based lifters and methodologies
- [ ] Avoids detailed form corrections (mentions general movement patterns only)
- [ ] Provides programming, plateau-breaking, and recovery advice
- [ ] Unlimited conversations for premium users
- [ ] Consistent personality across all interactions

**Technical Requirements**:
- Advanced AI service with personality prompting
- Premium user verification system
- Conversation history storage and context management
- Integration with user's workout data for context

---

### 🔥 **Story 4: Automatic Plateau Detection System**
**Priority**: P1 (High Value)

**As a** premium user  
**I want** Dr. Lift to automatically detect when my lifts plateau  
**So that** I can get proactive coaching without having to identify problems myself

**Acceptance Criteria**:
- [ ] Detects plateaus when exercise weight/reps unchanged for 3+ weeks
- [ ] Triggers automatic conversation: "I noticed your bench press hasn't improved in 3 weeks..."
- [ ] Different thresholds based on workout frequency (adjust for 2x/week vs 3x/week)
- [ ] Analyzes multiple exercises independently
- [ ] Provides plateau-specific diagnostic questions
- [ ] Offers evidence-based plateau-breaking strategies

**Technical Requirements**:
- Enhance existing workout analytics to detect stagnation patterns
- Create plateau detection algorithms
- Automated notification system
- Integration with Dr. Lift conversation system

---

### 🔥 **Story 5: Injury-Aware Coaching Integration**
**Priority**: P1 (Safety Critical)

**As a** premium user with injury history  
**I want** Dr. Lift to consider my injuries when providing advice  
**So that** I can train safely while still making progress

**Acceptance Criteria**:
- [ ] Dr. Lift accesses injury data from onboarding
- [ ] Modifies exercise recommendations based on injury limitations
- [ ] Asks injury-specific follow-up questions when relevant
- [ ] Provides alternative exercises for restricted movements
- [ ] Includes recovery-focused advice for injury management
- [ ] Never suggests exercises that contraindicate stated injuries

**Technical Requirements**:
- Injury data integration with AI coaching logic
- Exercise contraindication database
- Safety-first prompt engineering for Dr. Lift
- Integration with existing exercise library

---

### 📈 **Story 6: AI Assistant Spontaneous Tips**
**Priority**: P2 (Engagement)

**As a** free tier user  
**I want** to receive occasional unsolicited fitness tips from the AI assistant  
**So that** I can learn and stay engaged without using my conversation limit

**Acceptance Criteria**:
- [ ] AI provides 1-2 spontaneous tips per week maximum
- [ ] Tips are general fitness insights, not personalized analysis
- [ ] Tips don't count against conversation limit
- [ ] Users can disable spontaneous tips in settings
- [ ] Tips include subtle upgrade hints: "Premium users get personalized plateau analysis"

**Technical Requirements**:
- Tip scheduling and delivery system
- General fitness tip database
- User preference management for tip frequency

---

### 📈 **Story 7: Contextual Workout Analysis for Dr. Lift**
**Priority**: P2 (Premium Enhancement)

**As a** premium user  
**I want** Dr. Lift to reference my specific workout history in conversations  
**So that** I receive truly personalized coaching based on my actual training

**Acceptance Criteria**:
- [ ] Dr. Lift can reference specific workouts: "I see you hit a PR on squats last Tuesday"
- [ ] Analyzes workout patterns and trends
- [ ] Compares current performance to historical data
- [ ] Provides context-aware advice based on recent training
- [ ] Integrates with existing progress tracking data

**Technical Requirements**:
- Workout data API integration with AI system
- Enhanced data analysis capabilities
- Contextual prompt engineering for personalized responses

---

### 📈 **Story 8: Premium Upgrade Conversion Flow**
**Priority**: P2 (Business Critical)

**As a** free tier user who has used all conversations  
**I want** a smooth upgrade flow to premium  
**So that** I can continue getting AI coaching

**Acceptance Criteria**:
- [ ] Clear upgrade prompts when conversation limit reached
- [ ] Preview of Dr. Lift capabilities and plateau detection
- [ ] Smooth in-app purchase flow
- [ ] Immediate access to premium features after upgrade
- [ ] Onboarding tour for new premium AI features

**Technical Requirements**:
- In-app purchase integration
- Premium feature activation system
- Upgrade flow UI/UX implementation

---

## Implementation Priority

### Phase 1 (MVP): Core Functionality
1. **Story 1**: Onboarding Gate (enables safe AI access)
2. **Story 2**: Free Tier AI Assistant (showcases value)
3. **Story 3**: Premium Dr. Lift Coach (core premium feature)

### Phase 2: Intelligence Features  
4. **Story 4**: Plateau Detection (key differentiator)
5. **Story 5**: Injury-Aware Coaching (safety enhancement)

### Phase 3: Engagement & Conversion
6. **Story 8**: Upgrade Conversion Flow (business optimization)
7. **Story 6**: Spontaneous Tips (engagement)
8. **Story 7**: Contextual Analysis (premium enhancement)

---

## Technical Integration Points

### Existing Liftrix Infrastructure to Leverage:
- **Coach Screen**: Replace placeholder with AI chat interface
- **Firebase AI**: Activate unused dependency for AI services
- **Analytics Service**: Enhance for plateau detection
- **Workout Data**: Feed into AI for contextual coaching
- **User Profiles**: Store conversation limits and premium status

### New Components Required:
- AI service layer with personality management
- Conversation tracking and limit enforcement
- Plateau detection algorithms
- Injury data integration system
- Premium upgrade flow