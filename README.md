# File Manager Pro with AI Agent

**Open Source MT Manager Alternative with Gemini AI Integration**

A powerful Android File Manager with advanced APK editing capabilities and integrated AI coding assistant, built on top of the popular Amaze File Manager.

## Features

### File Management
- Complete file management operations (copy, move, delete, rename)
- Root explorer support for advanced users
- Support for multiple cloud storage providers
- FTP/SFTP server functionality
- Compressed file support (ZIP, RAR, 7z, TAR, etc.)
- Tab-based interface for multitasking

### APK Editor (MT Manager Style)
- **APK Decompilation**: Extract APK contents for editing
- **AndroidManifest.xml Editor**: Modify package name, permissions, components
- **Smali Code Editor**: Edit Dalvik bytecode directly
- **APK Signing**: Sign modified APKs with test keys or custom keystores
- **APK Information Viewer**: View detailed APK metadata
- **Security Analysis**: Analyze APKs for security vulnerabilities

### AI Assistant (Powered by Gemini)
- **Code Explanation**: Get explanations for smali code, manifest configurations
- **Error Analysis**: Understand and fix code errors with AI assistance
- **Code Modification**: Describe changes in natural language, AI applies them
- **Security Audit**: Deep security analysis of AndroidManifest.xml
- **Smali to Java Conversion**: Convert smali bytecode to readable Java code
- **Code Optimization**: Get suggestions for code improvements

## Screenshots

| Files View | APK Editor | AI Assistant |
|------------|------------|--------------|
| ![](screenshots/files.png) | ![](screenshots/apk_editor.png) | ![](screenshots/ai_assistant.png) |

## Building

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK 35

### Setup

1. Clone the repository:
```bash
git clone https://github.com/yourusername/AI_File_Manager.git
cd AI_File_Manager
```

2. Create `local.properties` with your Gemini API key:
```properties
GEMINI_API_KEY=your_api_key_here
```

3. Build the project:
```bash
./gradlew assembleFdroidDebug
```

### Getting a Gemini API Key

1. Visit [Google AI Studio](https://aistudio.google.com/)
2. Create an API key
3. Add it to your `local.properties` file

## Project Structure

```
app/src/main/java/com/amaze/filemanager/
├── apkeditor/                    # APK editing functionality
│   ├── ApkEditorEngine.kt        # Main APK editing engine
│   ├── ApkInfo.kt               # APK metadata data classes
│   ├── ApkSigner.kt             # APK signing implementation
│   ├── ManifestEditor.kt        # AndroidManifest.xml editing
│   └── SmaliEditor.kt           # Smali code parsing/editing
├── ai/                          # AI integration
│   └── AiAssistantService.kt    # Gemini API integration
├── ui/fragments/apkeditor/      # UI for APK editing
│   ├── ApkViewerFragment.kt     # APK content viewer
│   ├── ApkFileEditorFragment.kt # File editor with AI
│   └── AiAssistantFragment.kt   # AI chat interface
└── ...
```

## Dependencies

### APK Editing
- **Smali/Baksmali**: For smali code manipulation
- **Jadx**: DEX to Java decompilation
- **ApkSigner**: APK signing support

### AI Integration
- **OkHttp**: HTTP client for Gemini API
- **Gson**: JSON serialization

### Core
- **Kotlin**: Primary language
- **AndroidX**: Modern Android libraries
- **Material Components**: UI components
- **RxJava**: Reactive programming

## Security Notes

### APK Signing
- Test keys are provided for development purposes only
- **Never distribute APKs signed with test keys in production**
- Use your own keystore for production releases

### AI Features
- API key should be set via `local.properties`
- Never commit API keys to version control
- All AI requests are sent securely via HTTPS

## License

```
Copyright (C) 2014-2024 Arpit Khurana, Vishal Nehra, Emmanuel Messulam, Raymond Lai and Contributors.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```

## Acknowledgments

- **Amaze File Manager Team**: For the excellent base file manager
- **Google**: For the Gemini API
- **MT Manager**: For inspiration on APK editing features
- **All Contributors**: For making this project better

## Contributing

Contributions are welcome! Please read our [Contributing Guidelines](CONTRIBUTING.md) before submitting PRs.

## Version History

### v1.0-AI-Beta
- Initial release with APK editing capabilities
- AI Assistant integration with Gemini
- Security analysis features
- Smali code editing and analysis
