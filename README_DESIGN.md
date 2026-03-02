# Kurdish TV App - Design Documentation

This document outlines the design principles and structure of the Kurdish TV App, focusing on the user interface and user experience of each page.

## 🎨 Overall Design Philosophy

- **Modern & Clean**: The app utilizes Material Design 3 to provide a modern, clean, and intuitive interface.
- **User-Friendly**: The design prioritizes ease of use, with a logical flow and clear visual hierarchy.
- **Consistent**: A consistent design language is used across all screens, including colors, typography, and iconography.
- **Responsive**: The layout is designed to be responsive and work well on various screen sizes, from phones to tablets.

## 🎨 Color Palette

The app uses a color palette inspired by the Kurdish flag, with a modern twist to ensure a pleasant viewing experience in both light and dark modes.

| Color Name | Light Theme | Dark Theme |
| :--- | :---: | :---: |
| **Primary** | `#00643C` | `#72DA9D` |
| **Secondary** | `#98403D` | `#FFB3AF` |
| **Tertiary** | `#FFC107` | `#FFD54F` |
| **Background** | `#F7FBF2` | `#171D19` |
| **Surface** | `#F7FBF2` | `#171D19` |

## 📄 Pages Design

### 1. Splash Screen
A professional splash screen has been implemented for a better app launch experience on Android 12 and above.

### 2. Home Page (`MainActivity`)
- **Layout**: A clean, organized layout with a clear hierarchy of information.
- **Search Bar**: A prominent, easy-to-use search bar at the top of the screen.
- **Category Filters**: Modern `Chip` components for filtering channels by category.
- **Channel Grid**: A visually appealing grid of channels, displaying only the channel logos for a clean and simple look.
- **Recommended Section**: A horizontal `RecyclerView` to showcase popular and recommended channels.
- **View Mode Toggle**: An option to switch between a grid view and a list view.
- **Floating Action Button (FAB)**: A FAB for quick access to the settings page.

![Home Page](...)

### 3. Player Page (`PlayerActivity`)
- **Immersive Mode**: The player starts in a fully immersive mode, hiding all system bars for a distraction-free viewing experience.
- **Clean Interface**: The top toolbar has been removed to provide a cleaner look. The system's back gesture is used to exit the player.
- **Advanced Controls**: The player controls are part of the `PlayerView` and appear on screen tap, including:
  - Play/Pause, Fast Forward/Rewind
  - Playback speed control
  - Audio track and subtitle selection
  - Video quality selection
  - Volume control

![Player Page](...)

### 4. Settings Page (`SettingsActivity`)
- **User-Friendly Layout**: Settings are grouped into logical sections: "General," "Appearance," and "Support."
- **Interactive Controls**: `MaterialSwitch` components and clickable `TextViews` with icons make it easy to understand and interact with each option.
- **Sections**:
  - **General**: Auto-play, HD quality preference.
  - **Appearance**: Dark mode toggle, theme selection (Light, Dark, System Default).
  - **Support**: About, Contact Us, Rate App.

![Settings Page](...)

### 5. About Page (`AboutActivity`)
- **Professional Look**: A clean and professional "About" page with the app icon, name, version number, and contact information.
- **Standard Navigation**: A toolbar with a back button for easy navigation.

![About Page](...)

## 💡 User Experience (UX) Considerations

- **Simplicity**: The design focuses on simplicity and ease of use, removing unnecessary clutter.
- **Intuitiveness**: The app is designed to be intuitive, with clear visual cues and a logical flow.
- **Accessibility**: The design considers accessibility, with good contrast ratios and clear touch targets.
- **Performance**: The design is optimized for performance, with efficient image loading and smooth animations.