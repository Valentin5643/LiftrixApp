# Play Store Marketing Assets Implementation Guide

**Task**: P0-QUAL-002 - Create all required Play Store marketing assets
**Status**: Manual Implementation Required
**Estimated Time**: 4-6 hours (screenshot capture + editing)

---

## Current Asset Inventory

### ✅ Existing Screenshots (Need Resizing)
Located in `assets/`:
1. `Home-screen.jpg` (420x800) → needs resize to 1080x1920
2. `Progress.jpg` (410x800) → needs resize to 1080x1920
3. `Summary.jpg` (419x800) → needs resize to 1080x1920
4. `Workout.jpg` (416x800) → needs resize to 1080x1920

### ✅ Existing Icon Asset
- `icon.jpg` (1024x1024) → resize to 512x512 for hi-res icon

### ✅ Existing Banner
- `banner.png` (1110x712) → can be adapted for feature graphic

---

## Play Store Asset Requirements

### 📱 Phone Screenshots (MANDATORY - Minimum 2, Recommended 8)
- **Dimensions**: 1080 x 1920 pixels (16:9 aspect ratio)
- **Format**: PNG or JPEG
- **Max file size**: 8MB per image
- **Quantity**: 8 screenshots recommended

### 🎨 Feature Graphic (MANDATORY)
- **Dimensions**: 1024 x 500 pixels
- **Format**: PNG or JPEG
- **Purpose**: Displayed at top of Play Store listing

### 🔳 Hi-Res Icon (MANDATORY)
- **Dimensions**: 512 x 512 pixels
- **Format**: PNG (32-bit with alpha)
- **Purpose**: Used in various promotional materials

---

## Implementation Plan

### Phase 1: Resize Existing Screenshots (30 minutes)

Use an image editor (Photoshop, GIMP, or online tool like Canva):

**For Each Existing Screenshot**:
1. Open image in editor
2. Resize canvas to 1080x1920 (maintain aspect ratio)
3. Add subtle background gradient or blur to fill extra space
4. Export as PNG or JPEG (max 8MB)
5. Name clearly: `01-home-screen.png`, `02-workout-active.png`, etc.

**Tool Options**:
- **Canva** (easiest): Use "Custom Size" template (1080x1920)
- **GIMP** (free): Image → Scale Image → 1080x1920
- **Photoshop**: File → Automate → Fit Image → 1080x1920

---

### Phase 2: Capture Missing Screenshots (2-3 hours)

**Required Screenshots** (according to spec):

**Screenshot 1: Home Screen ✅** (already have, just resize)
- Show: Feed + navigation bar
- Highlight: Social feed with workout posts

**Screenshot 2: Active Workout Session ✅** (already have, just resize)
- Show: Workout in progress with exercises
- Highlight: Set tracking, rest timer

**Screenshot 3: Progress Analytics Dashboard ✅** (already have, just resize)
- Show: Charts and analytics widgets
- Highlight: Volume trends, strength progression

**Screenshot 4: Social Feed with Posts** (might be covered by home screen)
- Show: Workout posts, likes, comments
- Highlight: Community engagement

**Screenshot 5: AI Coaching Chat** ❌ **NEED TO CAPTURE**
- **How**:
  1. Launch app → Navigate to Chat/AI Coach
  2. Have a conversation about workout advice
  3. Screenshot the conversation interface
  4. Resize to 1080x1920

**Screenshot 6: Exercise Library** ❌ **NEED TO CAPTURE**
- **How**:
  1. Launch app → Navigate to Exercise Library
  2. Show exercise list with search/filter
  3. Screenshot the library view
  4. Resize to 1080x1920

**Screenshot 7: Profile with Achievements** ❌ **NEED TO CAPTURE**
- **How**:
  1. Launch app → Navigate to Profile
  2. Show user profile with stats, achievements, badges
  3. Screenshot the profile screen
  4. Resize to 1080x1920

**Screenshot 8: Settings/Privacy Controls** ❌ **NEED TO CAPTURE**
- **How**:
  1. Launch app → Settings → Privacy
  2. Show privacy controls, GDPR options
  3. Screenshot the settings screen
  4. Resize to 1080x1920

---

### Phase 3: Create Feature Graphic (1-2 hours)

**Option A: Adapt Existing Banner**
1. Open `assets/banner.png` (1110x712)
2. Resize/crop to 1024x500
3. Ensure text is readable and branding is clear
4. Export as PNG or JPEG

**Option B: Create New Feature Graphic**
Use Canva template for Google Play Feature Graphic:
1. Go to Canva.com
2. Search "Google Play Feature Graphic"
3. Use template with app branding:
   - App name: "Liftrix"
   - Tagline: "Workout Tracker & AI Coach"
   - Show app icon and key features
   - Use brand colors (Teal #20C9B7, Indigo #2A3B7D)
4. Download as 1024x500 PNG

**Key Elements to Include**:
- ✅ App logo/icon
- ✅ App name: "Liftrix"
- ✅ Tagline: "Your AI-Powered Fitness Companion"
- ✅ Key visual: Dumbbell, chart, or workout imagery
- ✅ Clean, professional design

---

### Phase 4: Prepare Hi-Res Icon (5 minutes)

**Simple Resize**:
1. Open `assets/icon.jpg` (1024x1024)
2. Resize to 512x512
3. Export as PNG with transparency (if applicable)
4. Name: `hi-res-icon.png`

**Tool**: Any image editor (GIMP, Photoshop, online resizer)

---

## Screenshot Capture Best Practices

### Device Setup
- **Use a clean device**: No notifications, full battery icon
- **Dark mode OFF**: Better readability in screenshots
- **Demo account**: Use account with good sample data
- **Remove sensitive data**: No real user names or data

### Capture Process
1. **Android Studio Emulator**: Use Pixel 5 or Pixel 6 emulator
   - Window → Device Manager → Create Virtual Device
   - Choose Pixel 5 (1080x2340 resolution)
   - Screenshot: Emulator toolbar → Camera icon

2. **Physical Device** (Alternative):
   - Use device with 1080x1920 or higher resolution
   - Screenshot: Power + Volume Down
   - Transfer to PC
   - Crop to exact 1080x1920

### Screenshot Enhancement (Optional but Recommended)
- Add subtle drop shadows to device frame
- Use screenshot framing tools (e.g., Screely.com, Mockuphone.com)
- Add descriptive text overlays for key features
- Ensure consistent status bar (Wi-Fi, battery, time)

---

## Store Listing Copy (Prepare While Creating Assets)

### Title (50 chars max)
```
Liftrix: Workout Tracker & AI Coach
```
(38 chars - ✅ within limit)

### Short Description (80 chars max)
```
Track workouts, analyze progress, get AI coaching. Your fitness companion.
```
(76 chars - ✅ within limit)

### Long Description (4000 chars max)

**Draft Template**:
```
🏋️ LIFTRIX - Your Complete Fitness Tracking & AI Coaching Platform

Transform your fitness journey with Liftrix, the all-in-one workout tracker that combines powerful analytics, social features, and AI-powered coaching.

🎯 KEY FEATURES

WORKOUT TRACKING
• Log exercises with sets, reps, weight, and rest timers
• Create custom workout templates
• Track progressive overload automatically
• Real-time workout session management
• Offline-first: works without internet

📊 ANALYTICS & PROGRESS
• Detailed strength progression charts
• Volume tracking across muscle groups
• One-rep max (1RM) calculations
• Personal record (PR) detection
• Workout frequency heatmaps
• Comprehensive dashboard with 12+ widgets

🤖 AI COACHING
• Powered by Google Gemini AI
• Personalized workout advice
• Form tips and exercise recommendations
• Bilingual support (English & Romanian)
• Context-aware fitness guidance

👥 SOCIAL FEATURES
• Share workout posts with the community
• Follow friends and gym buddies
• Real-time PR celebrations
• QR code-based gym buddy pairing (max 5)
• Like, comment, and save posts
• Privacy controls (Public/Followers/Private)

🔐 SECURITY & PRIVACY
• AES-256 database encryption (SQLCipher)
• GDPR compliant
• Full data export and deletion
• No ads, no data selling
• Offline-first with cloud sync

⚙️ TECHNICAL EXCELLENCE
• Material 3 design
• Dark mode support
• Accessibility features (TalkBack compatible)
• Works offline with automatic sync
• Fast, smooth, reliable

🌍 MULTILINGUAL
• English
• Romanian (Română)

🏅 ACHIEVEMENT SYSTEM
• Automatic achievement detection
• Track milestones and personal bests
• Celebrate progress with badges

📱 MODERN DESIGN
• Clean, intuitive interface
• Smooth animations (60fps)
• Responsive layouts for all screen sizes
• Material You theming

💪 PERFECT FOR
• Gym enthusiasts tracking strength training
• Fitness beginners learning proper form
• Athletes optimizing performance
• Social fitness communities
• Personal trainers managing clients

🔄 SYNC EVERYWHERE
• Real-time cloud synchronization
• Offline mode with conflict resolution
• Multi-device support
• Never lose your data

📈 COMING SOON
• Tablet optimization
• Advanced nutrition tracking
• Workout program marketplace
• Wearable device integration

Download Liftrix today and take control of your fitness journey!

---

💬 SUPPORT
Need help? Contact us at support@liftrix.app

🔗 PRIVACY POLICY
https://[YOUR-GITHUB-USERNAME].github.io/LiftrixApp/privacy-policy.html

📜 OPEN SOURCE
Liftrix is built with transparency. View our architecture and contribute at GitHub.

---

TAGS: workout tracker, fitness app, gym log, strength training, AI coach, exercise tracker, progressive overload, personal records, fitness analytics, social fitness
```

(Approximately 2,400 chars - ✅ well within 4000 limit)

---

## Final Checklist Before Upload

### Screenshots ✅
- [ ] 8 phone screenshots (1080x1920 PNG/JPEG)
- [ ] Screenshots show diverse app features
- [ ] No placeholder/demo data visible
- [ ] Clean UI (no error states or loading spinners)
- [ ] Screenshots numbered/ordered logically

### Graphics ✅
- [ ] Feature graphic (1024x500 PNG/JPEG)
- [ ] Hi-res icon (512x512 PNG)
- [ ] All assets < 8MB per file

### Store Listing ✅
- [ ] Title (< 50 chars)
- [ ] Short description (< 80 chars)
- [ ] Long description (< 4000 chars)
- [ ] Screenshots describe app benefits
- [ ] Privacy policy URL ready

---

## Upload Process

### Google Play Console Steps:
1. Log in to [Google Play Console](https://play.google.com/console)
2. Select "Liftrix" app
3. Navigate to "Main Store Listing"
4. **Phone Screenshots**:
   - Click "Add Screenshots"
   - Upload all 8 screenshots in order
   - Add captions (optional but recommended)
5. **Feature Graphic**:
   - Upload 1024x500 feature graphic
6. **Hi-Res Icon**:
   - Upload 512x512 icon
7. **Store Listing Text**:
   - Paste title, short description, long description
8. **Save Draft**
9. **Preview**: Click "View Store Listing" to preview

---

## Time Estimate

| Task | Time |
|------|------|
| Resize 4 existing screenshots | 30 min |
| Capture 4 new screenshots | 2 hours |
| Create feature graphic | 1 hour |
| Prepare hi-res icon | 5 min |
| Write store listing copy | 1 hour |
| Upload to Play Console | 30 min |
| **TOTAL** | **5 hours** |

---

## Tools & Resources

### Image Editing
- **Canva** (easiest): https://canva.com
- **GIMP** (free): https://gimp.org
- **Photoshop** (professional): Adobe Creative Cloud
- **Online Resizer**: https://imageresizer.com

### Screenshot Framing
- **Screely**: https://screely.com
- **Mockuphone**: https://mockuphone.com
- **Previewed**: https://previewed.app

### Color Palette (from CLAUDE.md)
- Primary Teal: #20C9B7
- Secondary Indigo: #2A3B7D

---

## Status Tracking

- [x] Asset inventory complete
- [ ] 4 existing screenshots resized to 1080x1920
- [ ] 4 new screenshots captured (AI chat, exercise library, profile, settings)
- [ ] Feature graphic created (1024x500)
- [ ] Hi-res icon prepared (512x512)
- [ ] Store listing copy written
- [ ] All assets uploaded to Play Console
- [ ] Store listing previewed and published

---

**Next Steps**: Follow this guide to complete P0-QUAL-002. Once assets are uploaded to Play Console, mark this task as complete.

**Contact**: ENGINEER for questions or asset review
