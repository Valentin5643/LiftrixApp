# IARC Content Rating Guide for Liftrix

**Task**: P0-QUAL-004 - Complete IARC Content Rating Questionnaire
**Location**: Google Play Console → App Content → Content Rating
**Estimated Time**: 30-45 minutes
**Status**: Manual Play Console Task

---

## What is IARC?

**IARC** (International Age Rating Coalition) provides age and content ratings for apps and games across multiple platforms. Completing the IARC questionnaire is **MANDATORY** for app publication on Google Play Store.

**Why it matters:**
- Required for all apps before they can be published
- Determines age rating (e.g., Everyone, Teen 13+, Mature 17+)
- Affects app visibility and discoverability
- Incorrect ratings can lead to app removal

---

## Access the IARC Questionnaire

### Step 1: Navigate to Content Rating

1. Log in to [Google Play Console](https://play.google.com/console)
2. Select your app: **Liftrix**
3. In the left sidebar: **App Content** → **Content Rating**
4. Click **Start Questionnaire** or **Fill Out Questionnaire**

---

## Questionnaire Answers for Liftrix

Use these recommended answers based on Liftrix's features and content moderation system:

### Section 1: App Information

**Q: What is your email address?**
- **Answer:** `support@liftrix.app`

**Q: Select the category that best describes your app:**
- **Answer:** ☑️ **Health & Fitness**
  - *Rationale: Liftrix is a workout tracking and fitness app*

---

### Section 2: Violence

**Q: Does your app contain any violence?**
- **Answer:** ☐ No

**Q: Does your app contain realistic or animated depictions of violence?**
- **Answer:** ☐ No

**Q: Does your app contain fantasy violence?**
- **Answer:** ☐ No

**Rationale:** Liftrix is a fitness tracking app with no violent content.

---

### Section 3: Sexuality

**Q: Does your app contain any sexual or suggestive content?**
- **Answer:** ☐ No

**Q: Does your app contain nudity or sexual content?**
- **Answer:** ☐ No

**Rationale:** Liftrix is a fitness app. Progress photos are moderated to prohibit inappropriate content (see Community Guidelines).

---

### Section 4: Language

**Q: Does your app contain profanity or crude humor?**
- **Answer:** ☑️ **Yes** → **Select:** *Infrequent/Mild*

**Explanation:**
- User-generated content (UGC) may occasionally contain mild profanity
- Content reporting system allows users to flag inappropriate language
- Community Guidelines prohibit offensive language targeted at others
- Admin moderation tools remove violating content

**Q: Does your app contain sexual references or innuendos?**
- **Answer:** ☐ No

**Rationale:** UGC apps with community moderation should acknowledge potential for mild language.

---

### Section 5: Controlled Substances

**Q: Does your app contain references to or depictions of illegal drugs?**
- **Answer:** ☐ No

**Q: Does your app contain references to or depictions of alcohol, tobacco, or drugs?**
- **Answer:** ☐ No

**Rationale:** Community Guidelines prohibit promotion of PEDs (performance-enhancing drugs) and illegal substances.

---

### Section 6: Gambling

**Q: Does your app contain simulated gambling?**
- **Answer:** ☐ No

**Q: Does your app allow users to purchase loot boxes, gacha, or item packs?**
- **Answer:** ☐ No

**Rationale:** Liftrix has no gambling mechanics or randomized purchases.

---

### Section 7: User Interaction

**⚠️ CRITICAL SECTION FOR UGC APPS**

**Q: Can users communicate with each other in your app?**
- **Answer:** ☑️ **Yes**

**Follow-up Q: Can users share their location with other users?**
- **Answer:** ☐ **No**
  - *Rationale: Location permissions removed (P0-SEC-002 completed)*

**Follow-up Q: Can users share personal information with other users?**
- **Answer:** ☑️ **Yes** → **Explain:**
  ```
  Users can share workout posts, comments, and profile information (username, bio,
  progress photos) with other users based on their privacy settings (Public,
  Followers Only, or Private). Users have full control over visibility settings
  and can block other users. All content is moderated via user reporting and
  admin moderation tools.
  ```

**Follow-up Q: Does your app include a content reporting feature?**
- **Answer:** ☑️ **Yes**

**Follow-up Q: Can users view user-generated content (UGC)?**
- **Answer:** ☑️ **Yes**

---

### Section 8: User-Generated Content (UGC)

**⚠️ CRITICAL SECTION - MUST BE ACCURATE**

**Q: Does your app allow users to create or upload content?**
- **Answer:** ☑️ **Yes**

**Q: What type of user-generated content can users create?**
- **Select ALL that apply:**
  - ☑️ **Text** (workout posts, comments, bios, chat messages)
  - ☑️ **Images/Photos** (progress photos, profile pictures)
  - ☐ Videos (not currently supported)
  - ☐ Audio (not supported)

**Q: Is user-generated content moderated?**
- **Answer:** ☑️ **Yes, all user-generated content is moderated**

**Follow-up Q: How is UGC moderated?**
- **Select ALL that apply:**
  - ☑️ **User reporting** (users can report inappropriate content)
  - ☑️ **Moderator review** (admin moderation tools review reports)
  - ☐ **Automated filters** (optional - not yet implemented)

**Follow-up Q: Describe your moderation process:**
  ```
  Liftrix uses a comprehensive multi-layered moderation system:

  1. USER REPORTING: All posts, comments, and profiles have a "Report Content"
     button accessible via the ⋮ menu. Users can report content for spam,
     inappropriate content, harassment, misinformation, or copyright violations.

  2. ADMIN MODERATION: Reports are reviewed by admin moderators within 24-48 hours.
     Moderators can issue warnings, remove content, suspend accounts (7-30 days),
     or permanently ban users based on severity.

  3. PRIVACY CONTROLS: Users can block other users, set profile/workout visibility
     to Public/Followers/Private, and hide from discovery.

  4. COMMUNITY GUIDELINES: Comprehensive guidelines prohibit harassment, spam,
     harmful health advice, inappropriate content, and hate speech. Guidelines
     are accessible at: https://[your-username].github.io/LiftrixApp/community-guidelines.html

  5. APPEALS PROCESS: Users can appeal moderation decisions via email to
     support@liftrix.app.

  All moderation actions are logged with audit trails for accountability.
  ```

**Q: Link to your Community Guidelines or Moderation Policy:**
- **Answer:** `https://[your-username].github.io/LiftrixApp/community-guidelines.html`
  - ⚠️ **Replace `[your-username]` with your actual GitHub username**

---

### Section 9: Advertising

**Q: Does your app display ads?**
- **Answer:** ☐ **No**

**Rationale:** Liftrix does not currently display advertisements.

*If you plan to add ads in the future, you'll need to update the content rating.*

---

### Section 10: In-App Purchases

**Q: Does your app contain in-app purchases?**
- **Answer:** ☑️ **Yes** (if premium features planned)
  - **OR:** ☐ **No** (if not yet implemented)

**If Yes, follow-up Q: What can users purchase?**
- **Select:**
  - ☑️ **Additional app functionality or content** (premium features, advanced analytics, etc.)
  - ☐ Digital goods (not applicable)
  - ☐ Physical goods (not applicable)

---

## Expected IARC Rating

Based on the answers above, Liftrix will likely receive:

### Age Ratings by Region

| Rating System | Expected Rating | Age Restriction |
|---------------|-----------------|-----------------|
| **ESRB** (US) | Teen | 13+ |
| **PEGI** (Europe) | PEGI 7 or PEGI 12 | 7+ or 12+ |
| **USK** (Germany) | USK 6 or USK 12 | 6+ or 12+ |
| **ClassInd** (Brazil) | L or 10 | All or 10+ |
| **IARC Generic** | 13+ | 13+ |

**Most Likely:** **Teen (13+)** due to user interaction and UGC

---

## Rating Factors

### Why Teen 13+ (Not Everyone)?

1. **User interaction:** Users can communicate via posts and comments
2. **User-generated content:** Users create and share content publicly
3. **Social features:** Following, liking, commenting enabled
4. **Mild language possible:** UGC may contain infrequent/mild profanity (moderated)

### Why NOT Mature 17+?

1. **No explicit content:** Community Guidelines prohibit sexual/violent content
2. **Strong moderation:** Content reporting + admin review system
3. **Privacy controls:** Users control who sees their content
4. **Health/fitness focus:** Educational and motivational content

---

## Submission Process

### Step 1: Complete Questionnaire
1. Answer all questions accurately using the guidance above
2. Review all answers before submitting
3. Click **Save** at the bottom of each section

### Step 2: Submit for Rating
1. After completing all sections, click **Submit**
2. IARC will generate your ratings (instant, no review period)
3. Ratings will appear in Play Console within minutes

### Step 3: Accept Ratings
1. Review the ratings assigned by IARC
2. If satisfactory, click **Accept**
3. Ratings are now applied to your app

### Step 4: Verify in Play Console
1. Navigate to **Store Presence → Main Store Listing**
2. Verify age rating displays correctly
3. Check that content descriptors match your expectations

---

## Common Mistakes to Avoid

### ❌ Incorrect Answers

**Mistake:** Selecting "No" for user interaction when app has social features
- **Consequence:** App may be removed for incorrect rating
- **Fix:** Answer "Yes" and describe moderation system

**Mistake:** Not disclosing UGC moderation system
- **Consequence:** Rating may be higher than necessary (Mature 17+)
- **Fix:** Describe reporting + admin moderation in detail

**Mistake:** Providing dead link to Community Guidelines
- **Consequence:** Rating submission rejected
- **Fix:** Ensure GitHub Pages is enabled and URL is correct

### ✅ Best Practices

1. **Be honest:** Incorrect ratings can lead to app removal
2. **Be specific:** Detailed moderation descriptions help achieve appropriate rating
3. **Update when features change:** Re-submit if you add ads, new UGC types, or gambling
4. **Test the Community Guidelines link:** Click it before submitting to verify it works

---

## After Submission

### What Happens Next?

1. **Instant rating:** IARC generates ratings immediately (no wait time)
2. **Accept ratings:** Review and accept the assigned ratings
3. **Publish:** Ratings are now part of your app listing
4. **Monitor:** If you receive reports of inappropriate content, address immediately to maintain rating

### Updating Your Rating

If you need to change your rating (e.g., after adding new features):

1. Navigate to **App Content → Content Rating**
2. Click **Edit** or **Retake Questionnaire**
3. Update answers
4. Resubmit for new rating

**Note:** Changing features may require rating updates (e.g., adding ads, gambling, or new UGC types).

---

## Checklist Before Submission

- [ ] GitHub Pages enabled for Community Guidelines
- [ ] Community Guidelines URL is publicly accessible
- [ ] All social features (posts, comments, follows) accurately disclosed
- [ ] Moderation system described comprehensively
- [ ] Location sharing correctly marked as "No" (P0-SEC-002 completed)
- [ ] In-app purchases status correctly marked (Yes/No based on implementation)
- [ ] Contact email is correct (support@liftrix.app)
- [ ] All sections of questionnaire completed
- [ ] Answers reviewed for accuracy

---

## Support & Resources

### IARC Help
- **Official Guide:** https://www.globalratings.com/how-iarc-works.aspx
- **Google Play Help:** https://support.google.com/googleplay/android-developer/answer/9859655

### Liftrix Documentation
- **Community Guidelines:** `docs/community-guidelines.html`
- **Moderation System:** `CONTENT_MODERATION_SYSTEM.md`
- **Privacy Policy:** `docs/privacy-policy.html`

### Contact
- **Questions:** support@liftrix.app
- **Appeals:** Email with subject "IARC Rating Question"

---

## Estimated Time

| Step | Time |
|------|------|
| Read this guide | 10 min |
| Complete questionnaire | 20 min |
| Review and submit | 5 min |
| Accept ratings | 2 min |
| Verify in Play Console | 3 min |
| **TOTAL** | **40 minutes** |

---

**Status**: Ready for implementation
**Prerequisites**:
- ✅ GitHub Pages enabled
- ✅ Community Guidelines published
- ✅ Moderation system documented

**Next Steps**: Complete IARC questionnaire in Play Console using this guide

---

**Document Version**: 1.0
**Created**: 2024-12-26
**Owner**: Liftrix Engineering Team
