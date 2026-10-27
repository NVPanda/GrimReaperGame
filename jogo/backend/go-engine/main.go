package main

import (
	"encoding/json"
	"log"
	"net/http"
	"sync"
	"time"
)

type EntityState struct {
	ID        string  `json:"id"`
	X         float64 `json:"x"`
	Y         float64 `json:"y"`
	Z         float64 `json:"z"`
	Direction int     `json:"dir"`   // 0: Down, 1: Left, 2: Right, 3: Up (Padrão Bakin 3x4)
	Frame     int     `json:"frame"` // 0, 1, 2
}

type WorldEngine struct {
	mu       sync.RWMutex
	Entities map[string]*EntityState
}

func NewWorldEngine() *WorldEngine {
	return &WorldEngine{
		Entities: map[string]*EntityState{
			"hero":  {ID: "hero_adventurer", X: 320, Y: 240, Z: 0, Direction: 0, Frame: 1},
			"slime": {ID: "monster_forest_slime", X: 450, Y: 260, Z: 0, Direction: 1, Frame: 0},
		},
	}
}

// Loop autoritativo do jogo em 60 TPS (Ticks Por Segundo)
func (w *WorldEngine) StartGameLoop() {
	ticker := time.NewTicker(time.Second / 60)
	defer ticker.Stop()

	tickCount := 0
	for range ticker.C {
		w.mu.Lock()
		tickCount++
		// Atualiza frame de caminhada a cada 12 ticks (estilo Bakin)
		if tickCount%12 == 0 {
			for _, ent := range w.Entities {
				ent.Frame = (ent.Frame + 1) % 3
			}
		}
		w.mu.Unlock()
	}
}

func main() {
	engine := NewWorldEngine()
	go engine.StartGameLoop()

	http.HandleFunc("/api/state", func(rw http.ResponseWriter, r *http.Request) {
		rw.Header().Set("Access-Control-Allow-Origin", "*")
		rw.Header().Set("Content-Type", "application/json")
		engine.mu.RLock()
		defer engine.mu.RUnlock()
		_ = json.NewEncoder(rw).Encode(engine.Entities)
	})

	log.Println("[Go Game Engine] Rodando Game Loop 60 TPS na porta :8085")
	log.Fatal(http.ListenAndServe(":8085", nil))
}