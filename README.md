# VoDo.txt (ˈvü-ˌdü ˈdät ˈtekst)

VoDo.txt was born from the end of development of *SimpleTask* and dissatisfaction with the current alternatives. With the exception of [recurrence](https://github.com/fleapower/vodotxt#-the-vodo-extensions-recurrence--last), VoDo.txt uses the standard **[todo.txt](https://github.com/todotxt/todo.txt/blob/master/README.md#todotxt-format-rules)** format with a focus on speed.

---

## Quick Start: Initial Setup
When you first launch VoDo.txt, you'll be greeted by the **Welcome Setup**.

1. **Select Storage Folder**: Click the button to choose a location on your device (e.g., your "Documents" folder).
2. **Automated Magic**: VoDo.txt will automatically create a `vodo.txt/` subfolder and initialize your `todo.txt` and `done.txt` (archive) files.
3. **External Access**: Because these are stored in a standard Android folder, you can open, edit, or sync them with any other app or computer.

---

## Google Drive Sync
Keep your tasks in sync across devices without a middleman.
- **Enable Sync**: In **Settings**, toggle "Enable Google Drive Sync."
- **Choose a Folder**: Select the folder on your Drive where you want the files to live.
- **Conflict Resolution**: If a task is changed on your phone and Drive simultaneously, VoDo.txt will detect the collision, create a backup in your **Conflicts Folder**, and ask you which version you want to keep.

---

## Managing Tasks

### Adding a Task
Tap the **[+]** button. The **Task Details** dialog will open:
- **Auto-Population**: If you have active filters (like `@work` or `+Project`), the task field is automatically prepopulated with those tags.
- **Smart Formatting**: Type naturally. VoDo.txt handles the protocol formatting (priorities, dates, tags) for you.
- **Creation Date**: If enabled in settings, the current date is added automatically.

### Editing a Task
Simply tap any task in the list to open the Details dialog. You can edit the raw text or use the quick-action icons for Priority, Due Date, Recurrence, Context, and Projects.

### Quick Add Shortcut
Long-press the **VoDo.txt app icon** on your home screen. You can launch a minimalist "Quick Add" popup or place the shortcut directly onto your home screen for one-tap entry.

### Quick Action
Automatically provides shortcuts for **Links**, **Emails**, and **Phone Numbers** found in your tasks.

---

## Interaction & Gestures

- **Swipe Right**: Quickly complete or postpone a task (configurable in Settings).
- **Swipe Left**: Open the **Filter Drawer**.
- **Long Press**: Enter **Selection Mode**.
  - Tap multiple tasks to select them.
  - **Bulk Actions**: Delete, change priority, set due dates, or toggle completion for all selected tasks at once using the icons in the bottom bar. Using "select all" will only select the tasks visible in the current filter view.
- **Drag and Drop**: In **File Sort** mode, long-press and hold, then drag to physically reorder your tasks.

---

## Filtering
The **Filter Drawer** (swipe from left) is the engine of VoDo.txt.

- **Ad-hoc Filtering**: Tap any Context (@) or Project (+) to instantly filter your list.
- **Inversion**: Tap the "Invert" checkbox to see everything *except* the selected tags (e.g., "Show me everything that isn't @work").
- **Saving Filters**: Create complex filter combinations and save them with a name.
  - **Draft Logic**: When you click "Create New Filter," the dialog is prepopulated with whatever ad-hoc filters you currently have active.
- **Closing**: Tap the "All Tasks" header or the "Clear" button to return to your full list.

---

## The VoDo Extensions: Recurrence & Last
While VoDo.txt follows the `todo.txt` standard, it adds two tags to handle repeating tasks.

### 1. Recurrence (`r:`)
The rule for recurrence is simple: **Where is the letter?**

| Syntax | Meaning | Example | Read as... |
| :--- | :--- | :--- | :--- |
| **Letter Last** | Every X units | `r:03w` | "Recur every 3 weeks" |
| **Letter First** | On a specific day | `r:w02` | "Weekly on the 2nd day (Monday)" |
| **Annual** | Every year | `r:y0304` | "Every March 4th" |

> NOTE: Recurrence days do not shift. If you have `r:w02` (Monday) and postpone the task to Tuesday, the *next* occurrence will still correctly fall on a Monday.

### 2. The Last Tag (`last:`)
Whenever a recurring task is completed, VoDo.txt automatically adds a `last:YYYY-MM-DD` tag. This keeps a record of exactly when you last performed that action. This can be disabled in settings.
> NOTE: After disabling last tags in settings, existing last tags will be removed as each individual recurring task is completed.

---

## Settings Reference
- **Appearance**: Customize font size, themes (Light/Dark/System), and toggle checkboxes.
- **Gestures**: Choose if a right-swipe completes or postpones a task.
- **Protocol Options**: Toggle "Auto-Creation Date" or choose to "Maintain Last Tag."
- **Archiving**: One-tap to move all completed tasks into your `done.txt` file.
- **Backup/Restore**: Export your entire app configuration (including all saved filters) to a JSON file.

---

[GitHub Repository](https://github.com/fleapower/vodotxt)
