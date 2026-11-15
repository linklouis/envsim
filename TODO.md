
## UI
### Agent Inspector
- [X] Move the generation
- [X] Remove desires predation
- [X] Dropdowns?
- [ ] "Go To Agent" button
- [X] Add a radar chart for food preference?
- [ ] Add units + a way to see stuff in metric, imperial, & pixels
- [ ] Add current turn speed
- [ ] Have the food preferences have the colors the refer to

### Agent Spawner
- [ ] Make it wide enough to fit the whole names
- [ ] Allow user to click on an agent to get details before spawning
- [ ] Are agents shown at full health in the thumbnail?
- [ ] A way to delete agents

### Agent Saving
- [X] Make the window match the style of everything else

### Main Menu
- [ ] Make a main menu
- [ ] Allow customizable default terrains
- [ ] **Allow saving an entire game state**
- [ ] Allow loading a terrain that's a different size than the current one
  - Scale agent positions
  - Respawn all food?

### Hamburger Menu
- [X] Make the sim speed slider logarithmic

### Terrain Editing
- [X] **Painting under food should reset the food's cached terrain**
- [ ] Add circular brushes
- [ ] Add filling?

### Debug Panel
- [X] Have the num predations counter be per frame rather than per tick?
- [X] Completely redo it

### MISC.
- [ ] Change the max zoom factor based on the world size
- [ ] Make the agents more visually distinct from food

## MISC.
- [ ] Add pixel to meter scale to size energy cost calculation
- [ ] Different foods more likely depending on location
  - Could just do certain foods only grow in certain terrain
  - Could do different food more likely to spawn in certain terrain (makes updating food based on terrain changes harder)
  - Could have food actually go away, and spawn new food
    - Could allow like having certain foods spawn more NEAR water, which would be super cool
- [ ] Add memory?
- [ ] Freezing stats for a run or just species?
- [ ] Have poison food actually take health?

## Stats
- [ ] Add stats screen
- [ ] Population
- [ ] Latest generation
- [ ] Earliest generation
- [ ] Oldest agent alive rn
- [ ] How much of the genome space is currently occupied, plus over time

## Bugs
- [ ] Bug where an agent you're tracking will just disappear, but its stats don't give a reason for why it would be dead
- [ ] All the Agents just randomly disappeared, but the number didn't change