import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.heftreng.app',
  appName: 'Heftreng',
  webDir: 'build',          // svelte.config.js → adapter-static → pages: "build"

  ios: {
    contentInset: 'automatic',   // notch / Dynamic Island safe-area
    backgroundColor: '#13131A',  // charcoal-dark, splash kapanana kadar görünür
    overrideUserInterfaceStyle: 'automatic', // dark/light sistem ayarına uyar
    scrollEnabled: true,
  },

  plugins: {
    SplashScreen: {
      launchShowDuration: 1200,
      launchAutoHide: true,
      backgroundColor: '#13131A',
      showSpinner: false,
    },
    StatusBar: {
      style: 'Default',
      backgroundColor: '#00000000',
      overlaysWebView: true,
    },
    PushNotifications: {
      presentationOptions: ['badge', 'sound', 'alert'],
    },
    Keyboard: {
      resize: 'body',
      resizeOnFullScreen: true,
    },
  },
};

export default config;
