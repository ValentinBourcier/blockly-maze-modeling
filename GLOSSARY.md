# Blocky Maze Modeling

A model-driven educational robotics maze environment combining an Eclipse Modeling Framework (EMF) domain model, a Blockly visual programming interface, and MOMoT evolutionary multi-objective program synthesis and repair.

## Language

### Maze & Simulation

**Pegman**:
The robotic actor navigating the grid maze according to the user's Blocky program.
_Avoid_: Avatar, player, sprite, agent

**GridMap**:
The two-dimensional discrete coordinate grid defining the maze's wall layout, start position, and goal position.
_Avoid_: Board, maze layout, terrain

**Cell**:
A single unit location in the GridMap with coordinates (x, y) and an assigned type (EMPTY, WALL, START, GOAL, DAMAGE).
_Avoid_: Tile, block, square

**SimulationEngine**:
The deterministic interpreter that executes an EMF solution program on a GridMap and produces an ExecutionTrace.
_Avoid_: Runner, interpreter, evaluator

**ExecutionTrace**:
The recorded sequence of GameState steps (position, orientation, status) produced by executing a program.
_Avoid_: History, log, path recorder

### Program Representation

**ProgramAST**:
The hierarchical EMF abstract syntax tree composed of Containers, Statements (AtomicStatement, Loop, IfStmt), and Bodies.
_Avoid_: Block structure, code tree, script

**BlockyAstMapper**:
The translator between Blockly workspace XML / JSON block structures and EMF ProgramAST elements.
_Avoid_: Parser, serializer, converter

### Search & Synthesis

**MomotSearch**:
The MOMoT search process using NSGA-II to evolve and synthesize candidate solution programs.
_Avoid_: Solver, genetic algorithm, optimizer

**DirectManipulation**:
User interaction placing a target marker on the maze grid to request synthesis of a program reaching that cell.
_Avoid_: Marker click, manual placement, goal pin

### Interactive Debugging & Telemetry

**InteractiveDebugger**:
The step-by-step debugger that steps through an ExecutionTrace, syncs Pegman's position, and re-simulates upon workspace changes.
_Avoid_: Stepper, client debugger, tracing controller

**TelemetryLogger**:
The logging subsystem recording user interaction events, iteration timing, and exploration metrics. Implemented by `TelemetryService`.
_Avoid_: Event tracker, analytics, audit log

**AdminReportService**:
The subsystem providing admin dashboard HTML generation, session event inspection, and JSON/HTML export capabilities.
_Avoid_: Admin controller, dashboard viewer

**BlockyJsBridgeController**:
The deep module coordinating two-way communication between the JavaFX application and the embedded Blockly WebView via JavaFX JSBridge.
_Avoid_: Bridge script, JS glue

**MomotUiCoordinator**:
The coordinator managing background MOMoT search execution, progress dispatching, and solution overlay visualization in the desktop UI.
_Avoid_: Search manager, MOMoT thread handler
