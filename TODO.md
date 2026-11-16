
## UI
### Agent Inspector
- [X] Move the generation
- [X] Remove desires predation
- [X] Dropdowns?
- [X] "Go To Agent" button
- [X] Add a radar chart for food preference?
- [X] Add units + a way to see stuff in metric, imperial, & pixels
- [X] Add current turn speed
- [X] Have the food preferences have the colors they refer to
- [X] Add commas in big numbers
- [ ] Have the "Follow" button actually follow
- [ ] Make it more clear when the agent you're following dies

### Agent Spawner
- [X] Make it wide enough to fit the whole names
- [X] Allow user to click on an agent to get details before spawning
- [X] A way to delete agents
- [X] Show agents at full energy in the thumbnail?
- [ ] Some stuff with this seems to run on the JFX thread, so it can cause the game to freeze.

### Agent Saving
- [X] Make the window match the style of everything else
- [ ] Give names?

### Main Menu
- [X] Make a main menu
- [X] Allow customizable default terrains
- [X] **Allow saving an entire game state**
- [X] Allow loading a terrain that's a different size than the current one
  - Scale agent positions
  - Respawn all food?

### Hamburger Menu
- [X] Make the sim speed slider logarithmic

### Terrain Editing
- [X] **Painting under food should reset the food's cached terrain**
- [X] Add circular brushes
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
- [X] Have poison food actually take health?
- [ ] Maximizing the window should reset the camera
- [ ] Should probably add better error handling in some spots

## Stats
- [ ] Add stats screen
- [ ] Population
- [ ] Latest generation
- [ ] Earliest generation
- [ ] Oldest agent alive rn
- [ ] How much of the genome space is currently occupied, plus over time

## Bugs
- [ ] Bug where an agent you're tracking will just disappear, but its stats don't give a reason for why it would be dead
- [ ] All the Agents just randomly disappeared, but the number of agents shown didn't change
- [X] Brain not using all the inputs?
- [ ] The default environments should be recreated each time so they match the current screen size